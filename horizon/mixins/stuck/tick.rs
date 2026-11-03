// Start of each server tick: run the queued searches and teleport.
fn stuck_tick(
    game: &mut crate::game::Game<'_>,
    context: &crate::game::native_lifecycle::NativeLifecycleContext,
    phase: crate::game::native_lifecycle::ServerTickPhase,
) {
    use crate::game::{native_lifecycle::ServerTickPhase, stuck as m};
    use horizon_client::text::{self, Text};
    if !matches!(phase, ServerTickPhase::ServerTickStart { .. }) {
        return;
    }
    let requests = m::take_requests();
    if requests.is_empty() {
        return;
    }
    let cfg = m::config();
    for r in requests {
        let spot = m::find(&cfg, &r.dimension, r.pos, |x, y, z| {
            game.native_server_block(context, &r.dimension, [x, y, z]).ok().map(|b| b.name)
        });
        let Some([x, y, z]) = spot else {
            game.native_tell(
                r.uuid,
                Text::literal("✘ Could not find a safe location. Please try again or contact an admin.").with_color(text::RED),
            );
            continue;
        };
        let line = format!("execute in {} run tp {} {}.5 {y} {}.5", r.dimension, r.name, x, z);
        if !game.native_run_command(&line) {
            continue;
        }
        m::used(r.uuid, std::time::Instant::now());
        if cfg.print_success {
            game.native_tell(
                r.uuid,
                Text::literal("⚑ There you go! You've been teleported to a safe location.").with_color(text::GREEN),
            );
        }
    }
}
