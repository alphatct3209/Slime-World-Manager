package com.grinderwolf.swm.nms.v1_21_R1;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.worldgen.WorldOptions;

import java.util.Optional;

public class CustomWorldData extends PrimaryLevelData {

    private final CraftSlimeWorld world;

    CustomWorldData(CraftSlimeWorld world) {
        super(
            new LevelSettings(
                world.getName(),
                GameType.NOT_SET,
                false,
                Difficulty.valueOf(world.getPropertyMap().getString(SlimeProperties.DIFFICULTY).toUpperCase()),
                false,
                new GameRules(),
                WorldDataConfiguration.DEFAULT
            ),
            new WorldOptions(0L, false, false),
            PrimaryLevelData.SpecialWorldProperty.NONE,
            SharedConstants.getCurrentVersion().getDataVersion().getVersion()
        );

        this.world = world;

        // Load game rules from extra data
        CompoundTag extraData = world.getExtraData();
        Optional<CompoundTag> gameRules = extraData.getAsCompoundTag("gamerules");
        gameRules.ifPresent(tag -> getGameRules().loadFromTag(
            (net.minecraft.nbt.CompoundTag) Converter.convertTag(tag), null
        ));
    }

    @Override
    public String getLevelName() {
        return world.getName();
    }

    @Override
    public boolean isInitialized() {
        return true;
    }
}
