// /stuck or /escape: check the cooldown and queue a search for the next tick.
fn stuck_command(
    game: &mut crate::game::Game<'_>,
    player: &crate::game::native_server::NativeServerPlayer,
    command: &str,
    ran: &mut bool,
) {
    use crate::game::stuck as m;
    use horizon_client::text::{self, Text};
    if *ran || !matches!(command.trim(), "stuck" | "escape") {
        return;
    }
    *ran = true;
    if let Some(left) = m::cooldown_left(player.uuid, std::time::Instant::now()) {
        game.native_feedback(
            Text::literal(format!("⚐ Please wait {left} seconds before using this command again.")).with_color(text::RED),
        );
        return;
    }
    game.native_feedback(Text::literal("⚐ Searching for a safe location...").with_color(text::YELLOW));
    let [x, y, z] = player.position;
    m::request(m::Request {
        uuid: player.uuid,
        name: player.name.clone(),
        dimension: player.dimension.clone(),
        pos: [x.floor() as i32, y.floor() as i32, z.floor() as i32],
    });
}
