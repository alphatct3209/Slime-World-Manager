package com.grinderwolf.swm.nms.v1_21_R1;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.nms.SlimeNMS;
import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R1.CraftWorld;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.IOException;

@Getter
public class v1_21_R1SlimeNMS implements SlimeNMS {

    private static final Logger LOGGER = LogManager.getLogger("SWM");

    private final byte worldVersion = 0x09;

    private boolean loadingDefaultWorlds = true;

    private CustomWorldServer defaultWorld;
    private CustomWorldServer defaultNetherWorld;
    private CustomWorldServer defaultEndWorld;

    public v1_21_R1SlimeNMS() {
        try {
            CraftCLSMBridge.initialize(this);
        } catch (NoClassDefFoundError ex) {
            LOGGER.error("Failed to find ClassModifier classes. Are you sure you installed it correctly?");
            System.exit(1);
        }
    }

    @Override
    public void setDefaultWorlds(SlimeWorld normalWorld, SlimeWorld netherWorld, SlimeWorld endWorld) {
        if (normalWorld != null) {
            World.Environment env = World.Environment.valueOf(normalWorld.getPropertyMap().getString(SlimeProperties.ENVIRONMENT).toUpperCase());

            if (env != World.Environment.NORMAL) {
                LOGGER.warn("The environment for the default world must always be 'NORMAL'.");
            }

            defaultWorld = createCustomWorldServer((CraftSlimeWorld) normalWorld, Level.OVERWORLD, World.Environment.NORMAL);
        }

        if (netherWorld != null) {
            World.Environment env = World.Environment.valueOf(netherWorld.getPropertyMap().getString(SlimeProperties.ENVIRONMENT).toUpperCase());
            defaultNetherWorld = createCustomWorldServer((CraftSlimeWorld) netherWorld, Level.NETHER, env);
        }

        if (endWorld != null) {
            World.Environment env = World.Environment.valueOf(endWorld.getPropertyMap().getString(SlimeProperties.ENVIRONMENT).toUpperCase());
            defaultEndWorld = createCustomWorldServer((CraftSlimeWorld) endWorld, Level.END, env);
        }

        loadingDefaultWorlds = false;
    }

    private CustomWorldServer createCustomWorldServer(CraftSlimeWorld world, ResourceKey<Level> dimension, World.Environment env) {
        try {
            CustomNBTStorage nbtStorage = new CustomNBTStorage(world);
            LevelStorageSource.LevelStorageAccess storageAccess = nbtStorage.createStorageAccess();
            MinecraftServer server = MinecraftServer.getServer();

            LevelStem levelStem = server.registries().compositeAccess()
                .registryOrThrow(Registries.LEVEL_STEM)
                .get(ResourceKey.create(Registries.LEVEL_STEM, dimension.location()));

            if (levelStem == null) {
                levelStem = server.registries().compositeAccess()
                    .registryOrThrow(Registries.LEVEL_STEM)
                    .get(LevelStem.OVERWORLD);
            }

            ChunkProgressListener progressListener = server.progressListenerFactory.create(11);

            return new CustomWorldServer(world, nbtStorage, storageAccess, dimension, levelStem, progressListener, env);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create world storage for " + world.getName(), ex);
        }
    }

    @Override
    public void generateWorld(SlimeWorld world) {
        String worldName = world.getName();

        if (Bukkit.getWorld(worldName) != null) {
            throw new IllegalArgumentException("World " + worldName + " already exists! Maybe it's an outdated SlimeWorld object?");
        }

        World.Environment env = World.Environment.valueOf(world.getPropertyMap().getString(SlimeProperties.ENVIRONMENT).toUpperCase());
        ResourceKey<Level> dimension = getOrCreateDimensionKey(worldName);

        CustomWorldServer server = createCustomWorldServer((CraftSlimeWorld) world, dimension, env);

        LOGGER.info("Loading world " + worldName);
        long startTime = System.currentTimeMillis();

        server.setReady(true);

        MinecraftServer mcServer = MinecraftServer.getServer();
        mcServer.addLevel(server);

        Bukkit.getPluginManager().callEvent(new WorldInitEvent(server.getWorld()));
        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(server.getWorld()));

        LOGGER.info("World " + worldName + " loaded in " + (System.currentTimeMillis() - startTime) + "ms.");
    }

    private ResourceKey<Level> getOrCreateDimensionKey(String worldName) {
        return ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("swm", worldName.toLowerCase().replace(' ', '_')));
    }

    @Override
    public SlimeWorld getSlimeWorld(World world) {
        CraftWorld craftWorld = (CraftWorld) world;

        if (!(craftWorld.getHandle() instanceof CustomWorldServer)) {
            return null;
        }

        CustomWorldServer worldServer = (CustomWorldServer) craftWorld.getHandle();
        return worldServer.getSlimeWorld();
    }

    @Override
    public CompoundTag convertChunk(CompoundTag tag) {
        net.minecraft.nbt.CompoundTag nmsTag = (net.minecraft.nbt.CompoundTag) Converter.convertTag(tag);
        int version = nmsTag.getInt("DataVersion");

        net.minecraft.nbt.CompoundTag newNmsTag = net.minecraft.util.datafix.DataFixers.getDataFixer()
            .update(net.minecraft.util.datafix.fixes.References.CHUNK,
                new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, nmsTag),
                version,
                net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion())
            .getValue();

        return (CompoundTag) Converter.convertTag("", newNmsTag);
    }
}
