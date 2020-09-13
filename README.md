# Inventory Value Overlay Plugin

The inventory value overlay plugin calculates a RuneLite users inventory value and displays it on the game screen as an overlay. 
![Overlay ](/screenshots/inventory_value_overlay_ss.png "Overlay")

## Usage
Available as part of the plugin-hub. Search "Inventory Value Overlay" on plugin-hub to install.

## Configuration Options
![Configuration](/screenshots/inventory_value_config_ss.png "Configuration")

### Use HighAlchemy Value
When checked, the inventory values calculation will use the coin value received from casting high alchemy on the item.

### Ignore Coins
When checked, the inventory value calculation will ignore coins in the user's inventory.

# Building the Plugin

The Inventory value plugin uses Gradle for building. Download and install Gradle, clone this repository, and then run 
```
gradle build
```     

Alternatively, you can use any modern IDE that supports Gradle projects. Clone the repository and then import the Gradle project into the IDE. You can then build the project using the IDE. 

## Functionally Testing the Plugin

You can test the plugin by running the `main` method in 'InventoryValueTest'. The test class will run the RuneLite client with the InventoryValue plugin loaded.
