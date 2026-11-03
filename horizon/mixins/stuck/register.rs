// /stuck and /escape for every player.
fn stuck_register(commands: &mut Vec<crate::game::native_server::NativeCommand>) {
    use crate::game::{native_server::NativeCommand, stuck as m};
    m::start(crate::game::Game::native_config_dir().ok().as_deref());
    commands.push(NativeCommand::new("stuck"));
    commands.push(NativeCommand::new("escape"));
}
