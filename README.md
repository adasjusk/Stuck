# Stuck? Plugin

## Description
The Unstuck plugin is designed for Minecraft Spigot servers to help players who are trapped in the bedrock layer at height 127. This plugin provides a simple commands to teleport players to a safer location, ensuring they can continue their adventure without being stuck. 

## Features
- **Stuck Command**: A command that players can use to teleport themselves out of the bedrock layer.
- **Safe Teleportation**: The plugin calculates a safe location above the bedrock layer or to a predefined safe zone.
- **Configurable Settings**: Server admins can configure the safe teleport location or the height to which players should be teleported.
- **Permission-Based Access**: Only players with the appropriate permissions can use the unstuck command, ensuring fair use.

## Commands
- `/stuck` - Teleports the player to a safer location

- `/escape` - Player escapes to a safer location

## Permissions
There is no need for them.

## Installation
1. Download the plugin unstuck.jar file.
2. Place the jar file in the `plugins` folder of your Spigot/Paper server.
3. Restart the server to load the plugin.
4. Configure the plugin settings in the generated `config.yml` file if necessary.

## Configuration
The `config.yml` file allows server admins to set the safe teleport location or the height to which players should be teleported. Example configuration:
```yaml
# UnstuckPlugin Configuration

# Time in seconds between uses of /stuck command
cooldown-seconds: 30

# Radius from current position to search for a safe spot
search-radius: 5

# Maximum number of attempts to find a safe location
max-attempts: 50

# Y-coordinate range for safe locations
# These are adjusted based on world min/max Y if necessary
min-y: 32
max-y: 120

# DO NOT TOUCH THIS!!
# Enable debug logging (only use for troubleshooting because it's meant for collecting log files)
debug: false
