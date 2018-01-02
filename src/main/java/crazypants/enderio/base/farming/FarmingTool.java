package crazypants.enderio.base.farming;

import javax.annotation.Nonnull;

import com.enderio.core.common.util.stackable.Things;

import crazypants.enderio.base.config.Config;
import crazypants.enderio.base.power.PowerHandlerUtil;
import crazypants.enderio.util.Prep;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

public enum FarmingTool {
  HAND,
  HOE(Config.farmHoes),
  AXE {
    @Override
    protected boolean match(@Nonnull ItemStack item) {
      return item.getItem().getHarvestLevel(item, "axe", null, null) >= 0;
    }
  },
  TREETAP,
  SHEARS {
    @Override
    protected boolean match(@Nonnull ItemStack item) {
      return item.getItem() instanceof ItemShears;
    }
  },
  NONE {
    @Override
    protected boolean match(@Nonnull ItemStack item) {
      return false;
    }
  };
  
  private final Things things;

  private FarmingTool(String... things) {
    this(new Things());
    for (String s : things) {
      this.things.add(s);
    }
  }
  
  private FarmingTool(Things things) {
    this.things = things;
  }

  public final boolean itemMatches(@Nonnull ItemStack item) {
    return Prep.isValid(item) && match(item);
  }
  
  public final Things getThings() {
    return things;
  }

  @SuppressWarnings("null")
  public static boolean isBrokenTinkerTool(@Nonnull ItemStack item) {
    return Prep.isValid(item) && item.hasTagCompound() && item.getTagCompound().hasKey("Stats")
        && item.getTagCompound().getCompoundTag("Stats").getBoolean("Broken");
  }

  protected boolean match(@Nonnull ItemStack item) {
    return things.contains(item);
  }

  public static boolean isTool(@Nonnull ItemStack stack) {
    for (FarmingTool type : values()) {
      if (type.itemMatches(stack)) {
        return true;
      }
    }
    return false;
  }

  public static FarmingTool getToolType(@Nonnull ItemStack stack) {
    for (FarmingTool type : values()) {
      if (type.itemMatches(stack)) {
        return type;
      }
    }
    return NONE;
  }

  public static boolean isDryRfTool(ItemStack stack) {
    IEnergyStorage cap = PowerHandlerUtil.getCapability(stack, null);
    return cap != null && cap.getMaxEnergyStored() > 0 && cap.getEnergyStored() <= 0;
  }

  public static boolean canDamage(@Nonnull ItemStack stack) {
    return stack.isItemStackDamageable() && stack.getItem().isDamageable();
  }

}
