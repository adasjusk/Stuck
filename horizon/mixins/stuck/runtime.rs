// Stuck for Horizon, the module crate::game::stuck and the Cargo library for cargo test.
use std::cell::{Cell, RefCell};
use std::collections::HashMap;
use std::path::Path;
use std::time::{Duration, Instant};

pub const NETHER_ROOF_Y: i32 = 127;
const DEFAULTS: &str = "# Stuck config (Horizon)
cooldown-seconds=30
search-radius=5
max-attempts=50
min-y=32
max-y=120
avoid-hazardous-blocks=true
avoid-unstable-blocks=true
print-success-message=true
";

#[derive(Clone, Debug, PartialEq)]
pub struct Config {
    pub cooldown_seconds: u64,
    pub search_radius: i32,
    pub max_attempts: u32,
    pub min_y: i32,
    pub max_y: i32,
    pub avoid_hazardous: bool,
    pub avoid_unstable: bool,
    pub print_success: bool,
}

/// config/stuck.properties with the Fabric keys, `debug` is ignored.
pub fn parse(text: &str) -> Config {
    let map: HashMap<&str, &str> = text
        .lines()
        .filter(|l| !l.trim_start().starts_with('#'))
        .filter_map(|l| l.split_once('='))
        .map(|(k, v)| (k.trim(), v.trim()))
        .collect();
    let int = |k: &str, d: i64| map.get(k).and_then(|v| v.parse().ok()).unwrap_or(d);
    let flag = |k: &str| map.get(k).is_none_or(|v| v.eq_ignore_ascii_case("true"));
    Config {
        cooldown_seconds: int("cooldown-seconds", 30).max(0) as u64,
        search_radius: int("search-radius", 5).clamp(0, 64) as i32,
        max_attempts: int("max-attempts", 50).clamp(1, 1000) as u32,
        min_y: int("min-y", 32) as i32,
        max_y: int("max-y", 120) as i32,
        avoid_hazardous: flag("avoid-hazardous-blocks"),
        avoid_unstable: flag("avoid-unstable-blocks"),
        print_success: flag("print-success-message"),
    }
}

/// A /stuck waiting for the next server tick, where blocks can be read.
pub struct Request {
    pub uuid: u128,
    pub name: String,
    pub dimension: String,
    pub pos: [i32; 3],
}

thread_local! {
    static CONFIG: RefCell<Config> = RefCell::new(parse(DEFAULTS));
    static LAST_USE: RefCell<HashMap<u128, Instant>> = RefCell::new(HashMap::new());
    static REQUESTS: RefCell<Vec<Request>> = const { RefCell::new(Vec::new()) };
    static SEED: Cell<u64> = Cell::new(
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map_or(1, |d| d.as_nanos() as u64 | 1),
    );
}

/// A world opens: read the config (writing the defaults when it is missing).
pub fn start(dir: Option<&Path>) {
    let text = dir.and_then(|d| {
        let path = d.join("stuck.properties");
        std::fs::read_to_string(&path).ok().or_else(|| {
            let _ = std::fs::create_dir_all(d).and_then(|()| std::fs::write(&path, DEFAULTS));
            None
        })
    });
    CONFIG.with(|c| *c.borrow_mut() = parse(text.as_deref().unwrap_or(DEFAULTS)));
    LAST_USE.with(|l| l.borrow_mut().clear());
    REQUESTS.with(|r| r.borrow_mut().clear());
}

pub fn config() -> Config {
    CONFIG.with(|c| c.borrow().clone())
}

/// Seconds `uuid` still waits before the next /stuck, if any.
pub fn cooldown_left(uuid: u128, now: Instant) -> Option<u64> {
    let wait = Duration::from_secs(config().cooldown_seconds);
    LAST_USE.with(|l| {
        let since = now.duration_since(*l.borrow().get(&uuid)?);
        (since < wait).then(|| (wait - since).as_secs().max(1))
    })
}

pub fn used(uuid: u128, now: Instant) {
    LAST_USE.with(|l| l.borrow_mut().insert(uuid, now));
}

pub fn request(r: Request) {
    REQUESTS.with(|q| q.borrow_mut().push(r));
}

pub fn take_requests() -> Vec<Request> {
    REQUESTS.with(|q| std::mem::take(&mut *q.borrow_mut()))
}

/// xorshift64: a number in 0..n.
fn roll(n: i32) -> i32 {
    SEED.with(|s| {
        let mut x = s.get();
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        s.set(x);
        (x % n.max(1) as u64) as i32
    })
}

fn bare(name: &str) -> &str {
    name.strip_prefix("minecraft:").unwrap_or(name)
}

fn air(name: &str) -> bool {
    matches!(bare(name), "air" | "cave_air" | "void_air")
}

// By name, as Horizon gives no collision shapes yet.
fn solid(name: &str) -> bool {
    let n = bare(name);
    const THROUGH: &[&str] = &[
        "water", "lava", "bubble_column", "short_grass", "tall_grass", "fern", "large_fern",
        "dead_bush", "seagrass", "kelp", "kelp_plant", "vine", "vines", "vines_plant", "torch", "sign", "button", "pressure_plate",
        "rail", "sapling", "banner", "cobweb", "flower", "tulip", "dandelion", "poppy",
        "orchid", "allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "lever",
    ];
    !air(n) && !THROUGH.iter().any(|t| n.ends_with(t))
}

pub fn hazardous(name: &str) -> bool {
    let n = bare(name);
    ["lava", "fire", "magma", "cactus", "sweet_berry", "campfire", "wither_rose", "pointed_dripstone"]
        .iter()
        .any(|h| n.contains(h))
        || (n.contains("copper") && n.contains("exposed"))
        || (n.contains("trial") && n.contains("spawner"))
}

fn unstable(name: &str) -> bool {
    let n = bare(name);
    n.contains("leaves") || n.contains("snow")
}

/// Solid ground with two air blocks above, highest in a random column nearby.
pub fn find(
    cfg: &Config,
    dimension: &str,
    [sx, sy, sz]: [i32; 3],
    block: impl Fn(i32, i32, i32) -> Option<String>,
) -> Option<[i32; 3]> {
    let nether = dimension == "minecraft:the_nether";
    // Vanilla's heights, custom dimension heights are not read.
    let (bottom, top) = if dimension == "minecraft:overworld" { (-64, 320) } else { (0, 256) };
    let (mut min_y, mut max_y) = if cfg.min_y == 32 && cfg.max_y == 120 {
        // The defaults stretch to the whole world, as in the Java builds.
        ((bottom + 5).max(-50), (top - 3).min(200))
    } else {
        ((bottom + 1).max(cfg.min_y), (top - 3).min(cfg.max_y))
    };
    if nether {
        max_y = max_y.min(NETHER_ROOF_Y - 1);
    }
    min_y = min_y.min(max_y);
    let roof = nether && sy >= NETHER_ROOF_Y;
    let radius = if roof { cfg.search_radius.max(16) } else { cfg.search_radius };
    let attempts = if roof { cfg.max_attempts.max(200) } else { cfg.max_attempts };
    let is_air = |x, y, z| block(x, y, z).is_some_and(|b| air(&b));
    for _ in 0..attempts {
        let x = sx + roll(radius * 2 + 1) - radius;
        let z = sz + roll(radius * 2 + 1) - radius;
        for y in (min_y..=max_y).rev() {
            let Some(ground) = block(x, y - 1, z) else {
                continue;
            };
            if nether && y - 1 >= 123 && bare(&ground) == "bedrock" {
                continue;
            }
            if solid(&ground)
                && !(cfg.avoid_unstable && unstable(&ground))
                && !(cfg.avoid_hazardous && hazardous(&ground))
                && is_air(x, y, z)
                && is_air(x, y + 1, z)
            {
                return Some([x, y, z]);
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    // Stone up to y 63, air above, lava at column x == 1.
    fn world(x: i32, y: i32, _z: i32) -> Option<String> {
        Some(match (x, y) {
            (1, 63) => "minecraft:lava",
            (_, ..=63) => "minecraft:stone",
            _ => "minecraft:air",
        }.into())
    }

    #[test]
    fn finds_the_surface() {
        let cfg = Config { search_radius: 0, ..parse(DEFAULTS) };
        assert_eq!(find(&cfg, "minecraft:overworld", [0, 10, 0], world), Some([0, 64, 0]));
        // Only the lava column: nowhere safe.
        assert_eq!(find(&cfg, "minecraft:overworld", [1, 10, 0], world), None);
        // Unloaded everywhere.
        assert_eq!(find(&cfg, "minecraft:overworld", [0, 10, 0], |_, _, _| None), None);
    }

    #[test]
    fn leaves_the_nether_roof() {
        let cfg = Config { search_radius: 0, ..parse(DEFAULTS) };
        let nether = |_: i32, y: i32, _: i32| {
            Some(match y {
                123..=127 => "minecraft:bedrock",
                ..=40 => "minecraft:netherrack",
                _ => "minecraft:air",
            }.into())
        };
        // On the roof the radius widens to 16: any column, under the roof.
        let spot = find(&cfg, "minecraft:the_nether", [0, 128, 0], nether).unwrap();
        assert_eq!(spot[1], 41);
    }

    #[test]
    fn reads_the_config() {
        let c = parse("cooldown-seconds = 5\nsearch-radius=x\navoid-unstable-blocks=false\n");
        assert_eq!((c.cooldown_seconds, c.search_radius, c.avoid_unstable), (5, 5, false));
        assert!(solid("minecraft:grass_block") && !solid("minecraft:short_grass"));
        assert!(!solid("minecraft:sunflower") && solid("minecraft:oak_planks"));
        assert!(hazardous("minecraft:magma_block") && !hazardous("minecraft:stone"));
    }

    #[test]
    fn waits_out_the_cooldown() {
        let now = Instant::now();
        assert_eq!(cooldown_left(3, now), None);
        used(3, now);
        assert_eq!(cooldown_left(3, now + Duration::from_secs(10)), Some(20));
        assert_eq!(cooldown_left(3, now + Duration::from_secs(30)), None);
    }
}
