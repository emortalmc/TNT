<img src="https://github.com/EmortalMC/TNT/blob/main/.github/tntlogo.png?raw=true" width="40%">

# TNT
TNT is an experimental world format for Minestom

## Cool stuff
- Designed for small worlds (global palette)
- Very fast loading times (23ms for my lobby - idk what Anvil is)
- Very small file size (~80kb in TNT vs ~13mb in Anvil for my lobby)
- [Converts from Anvil automatically](#anvil-conversion)
- [Could be loaded from databases, like Slime worlds](#tnt-sources)
- Stores block nbt (like sign text)
- Stores cached light from anvil (useful because Minestom doesn't have a light engine yet)

Unfortunately does not save entities (yet) as Minestom does not have entity (de)serialisation.

Also does not have world saving yet

# Usage
Creating a Minestom instance

```java
InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
TNTLoader tntLoader = new TNTLoader(instance, new FileTNTSource(Path.of("path/to/world.tnt")));
// or
TNTLoader tntLoader = new TNTLoader(instance, "path/to/world.tnt")
        
instance.setChunkLoader(tntLoader);
```

## Signs are blank? (or any other block has no data)
TNT needs some block handlers in order to load block data.

You can find some [example handlers in Immortal](https://github.com/EmortalMC/Immortal/tree/main/src/main/kotlin/dev/emortal/immortal/blockhandler) which are then registered like [this](https://github.com/EmortalMC/Immortal/blob/ea9f03249d01b7f2544bd96d588e6341d7bfbc99/src/main/kotlin/dev/emortal/immortal/ImmortalExtension.kt#L409)


## Anvil Conversion
In order for TNT to convert a TNT world automatically, the Anvil folder and TNT file need to be named the same and be in the same folder.

For example:
 - /worlds/world/ <- Anvil world folder
 - /worlds/world.tnt <- TNT world file (Put this path into the `TNTSource`)
 
You may also convert an anvil world to TNT manually with `TNT.convertAnvilToTNT(pathToAnvil, tntSaveSource)`
 
## TNT Sources
TNT worlds can be loaded and saved wherever you want (however only `FileTNTSource` is built in)

For example, you could make it read from Redis, MongoDB, MySQL or any sort of datastore.

You can do this by extending `TNTSource` and creating your own source.
