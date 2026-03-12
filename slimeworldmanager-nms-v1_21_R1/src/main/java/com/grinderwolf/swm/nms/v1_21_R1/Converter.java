package com.grinderwolf.swm.nms.v1_21_R1;

import com.flowpowered.nbt.Tag;
import com.flowpowered.nbt.*;
import com.grinderwolf.swm.api.utils.NibbleArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Converter {

    private static final Logger LOGGER = LogManager.getLogger("SWM Converter");

    static NibbleArray convertArray(net.minecraft.world.level.NibbleArray array) {
        if (array == null) {
            return null;
        }
        return new NibbleArray(array.getData());
    }

    static net.minecraft.world.level.NibbleArray convertArray(NibbleArray array) {
        return new net.minecraft.world.level.NibbleArray(array.getBacking());
    }

    static net.minecraft.nbt.Tag convertTag(Tag tag) {
        try {
            switch (tag.getType()) {
                case TAG_BYTE:
                    return net.minecraft.nbt.ByteTag.valueOf(((ByteTag) tag).getValue());
                case TAG_SHORT:
                    return net.minecraft.nbt.ShortTag.valueOf(((ShortTag) tag).getValue());
                case TAG_INT:
                    return net.minecraft.nbt.IntTag.valueOf(((IntTag) tag).getValue());
                case TAG_LONG:
                    return net.minecraft.nbt.LongTag.valueOf(((LongTag) tag).getValue());
                case TAG_FLOAT:
                    return net.minecraft.nbt.FloatTag.valueOf(((FloatTag) tag).getValue());
                case TAG_DOUBLE:
                    return net.minecraft.nbt.DoubleTag.valueOf(((DoubleTag) tag).getValue());
                case TAG_BYTE_ARRAY:
                    return new net.minecraft.nbt.ByteArrayTag(((ByteArrayTag) tag).getValue());
                case TAG_STRING:
                    return net.minecraft.nbt.StringTag.valueOf(((StringTag) tag).getValue());
                case TAG_LIST: {
                    net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                    ((ListTag<?>) tag).getValue().stream().map(Converter::convertTag).forEach(list::add);
                    return list;
                }
                case TAG_COMPOUND: {
                    net.minecraft.nbt.CompoundTag compound = new net.minecraft.nbt.CompoundTag();
                    ((CompoundTag) tag).getValue().forEach((key, value) -> compound.put(key, convertTag(value)));
                    return compound;
                }
                case TAG_INT_ARRAY:
                    return new net.minecraft.nbt.IntArrayTag(((IntArrayTag) tag).getValue());
                case TAG_LONG_ARRAY:
                    return new net.minecraft.nbt.LongArrayTag(((LongArrayTag) tag).getValue());
                default:
                    throw new IllegalArgumentException("Invalid tag type " + tag.getType().name());
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to convert NBT object:");
            LOGGER.error(tag.toString());
            throw ex;
        }
    }

    static Tag convertTag(String name, net.minecraft.nbt.Tag base) {
        switch (base.getId()) {
            case 1:
                return new ByteTag(name, ((net.minecraft.nbt.ByteTag) base).getAsByte());
            case 2:
                return new ShortTag(name, ((net.minecraft.nbt.ShortTag) base).getAsShort());
            case 3:
                return new IntTag(name, ((net.minecraft.nbt.IntTag) base).getAsInt());
            case 4:
                return new LongTag(name, ((net.minecraft.nbt.LongTag) base).getAsLong());
            case 5:
                return new FloatTag(name, ((net.minecraft.nbt.FloatTag) base).getAsFloat());
            case 6:
                return new DoubleTag(name, ((net.minecraft.nbt.DoubleTag) base).getAsDouble());
            case 7:
                return new ByteArrayTag(name, ((net.minecraft.nbt.ByteArrayTag) base).getAsByteArray());
            case 8:
                return new StringTag(name, base.getAsString());
            case 9: {
                List<Tag> list = new ArrayList<>();
                net.minecraft.nbt.ListTag originalList = (net.minecraft.nbt.ListTag) base;
                for (net.minecraft.nbt.Tag entry : originalList) {
                    list.add(convertTag("", entry));
                }
                return new ListTag<>(name, TagType.getById(originalList.getElementType()), list);
            }
            case 10: {
                net.minecraft.nbt.CompoundTag originalCompound = (net.minecraft.nbt.CompoundTag) base;
                CompoundTag compound = new CompoundTag(name, new CompoundMap());
                for (String key : originalCompound.getAllKeys()) {
                    compound.getValue().put(key, convertTag(key, originalCompound.get(key)));
                }
                return compound;
            }
            case 11:
                return new IntArrayTag(name, ((net.minecraft.nbt.IntArrayTag) base).getAsIntArray());
            case 12:
                return new LongArrayTag(name, ((net.minecraft.nbt.LongArrayTag) base).getAsLongArray());
            default:
                throw new IllegalArgumentException("Invalid tag type " + base.getId());
        }
    }

}
