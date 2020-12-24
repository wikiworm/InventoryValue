# Inventory Value Overlay Plugin

The inventory value overlay plugin calculates a RuneLite users inventory value and displays it on the game screen as an overlay.
 
![Overlay ](https://raw.githubusercontent.com/wikiworm/InventoryValue/dev/screenshots/inventory_value_overlay_ss.PNG "Overlay")

## Usage
Available as part of the plugin-hub. Search "Inventory Value Overlay" on plugin-hub to install.

## Configuration Options

![Configuration](https://user-images.githubusercontent.com/5294864/102701144-eea3d380-4221-11eb-9b66-bbb6d91408b3.png "Configuration")

### Use HighAlchemy Value
When checked, the inventory values calculation will use the coin value received from casting high alchemy on the item.

### Ignore Coins
When checked, the inventory value calculation will ignore coins in the user's inventory.

### Ignore Items
When a comma or semicolon separated list of item names is provided, those items will not be included in the inventory value.

![Ignoring Items](https://user-images.githubusercontent.com/5294864/102701261-2b23ff00-4223-11eb-97c6-0ccc197d2896.png)
![image](https://user-images.githubusercontent.com/5294864/102736363-bcbe6a00-4312-11eb-8417-bf0d69f517ac.png)

# Building the Plugin

The Inventory value plugin uses Gradle for building. Download and install Gradle, clone this repository, and then run 
```
gradle build
```     

Alternatively, you can use any modern IDE that supports Gradle projects. Clone the repository and then import the Gradle project into the IDE. You can then build the project using the IDE. 

## Functionally Testing the Plugin

You can test the plugin by running the `main` method in 'InventoryValueTest'. The test class will run the RuneLite client with the InventoryValue plugin loaded.
