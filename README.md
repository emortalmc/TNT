<img src="https://github.com/EmortalMC/TNT/blob/main/.github/tntlogo.png?raw=true" width="40%">

# TNT
TNT is an experimental world format for Minestom

## Cool stuff
 - Very small file size (~80kb in TNT vs ~13mb in Anvil)
 - Very fast loading times (23ms - idk what Anvil is)
 - Converts from Anvil automatically
 - Stores block nbt (like sign text)
 - Stores cached light from anvil (useful because Minestom doesn't have a light engine yet)

Unfortunately does not save entities (yet) as Minestom does not have entity (de)serialisation (yet)

Also does not have world saving yet

## Usage
Creating a Minestom instance

```java
// In Kotlin
val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
val tntLoader = TNTLoader(instance, Path.of("path/to/world.tnt"))
instance.chunkLoader = tntLoader

// In Java
InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
TNTLoader tntLoader = new TNTLoader(instance, Path.of("path/to/world.tnt"));
instance.setChunkLoader(tntLoader);
```
