package crazypants.enderio.machines.machine.tank;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.enderio.core.api.common.util.ITankAccess;
import com.enderio.core.common.fluid.FluidWrapper;
import com.enderio.core.common.fluid.SmartTank;
import com.enderio.core.common.fluid.SmartTankFluidHandler;
import com.enderio.core.common.util.FluidUtil;
import com.enderio.core.common.util.FluidUtil.FluidAndStackResult;
import com.enderio.core.common.util.ItemUtil;

import crazypants.enderio.base.EnderIO;
import crazypants.enderio.base.fluid.Fluids;
import crazypants.enderio.base.fluid.SmartTankFluidMachineHandler;
import crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity;
import crazypants.enderio.base.machine.baselegacy.SlotDefinition;
import crazypants.enderio.base.network.PacketHandler;
import crazypants.enderio.base.paint.IPaintable;
import crazypants.enderio.base.sound.SoundHelper;
import crazypants.enderio.base.sound.SoundRegistry;
import crazypants.enderio.base.xp.XpUtil;
import crazypants.enderio.machines.config.config.TankConfig;
import crazypants.enderio.util.Prep;
import info.loenwind.autosave.annotations.Storable;
import info.loenwind.autosave.annotations.Store;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

@Storable
public class TileTank extends AbstractInventoryMachineEntity implements ITankAccess.IExtendedTankAccess, IPaintable.IPaintableTileEntity {

  private static int IO_MB_TICK = 100;

  @Store
  protected final @Nonnull SmartTank tank;
  protected int lastUpdateLevel = -1;

  private boolean tankDirty = false;
  private int lastFluidLuminosity = 0;

  @Store
  private @Nonnull VoidMode voidMode = VoidMode.NEVER;

  public TileTank(EnumTankType tankType) {
    super(new SlotDefinition(0, 2, 3, 4, -1, -1));
    tank = tankType.getTank();
    tank.setTileEntity(this);
  }

  public TileTank() {
    this(EnumTankType.NORMAL);
  }

  @Override
  protected boolean doPush(@Nullable EnumFacing dir) {
    boolean res = super.doPush(dir);
    if (dir != null && tank.getFluidAmount() > 0) {
      if (FluidWrapper.transfer(tank, world, getPos().offset(dir), dir.getOpposite(), IO_MB_TICK) > 0) {
        setTanksDirty();
      }
    }
    return res;
  }

  @Override
  protected boolean doPull(@Nullable EnumFacing dir) {
    boolean res = super.doPull(dir);
    if (dir != null && tank.getFluidAmount() < tank.getCapacity()) {
      if (FluidWrapper.transfer(world, getPos().offset(dir), dir.getOpposite(), tank, IO_MB_TICK) > 0) {
        setTanksDirty();
      }
    }
    return res;
  }

  private int getFilledLevel() {
    int level = (int) Math.floor(16 * tank.getFilledRatio());
    if (level == 0 && tank.getFluidAmount() > 0) {
      level = 1;
    }
    return level;
  }

  public boolean canVoidItems() {
    return tank.getFluid() != null && tank.getFluid().getFluid().getTemperature() > 973;
  }

  public @Nonnull VoidMode getVoidMode() {
    return voidMode;
  }

  public void setVoidMode(@Nonnull VoidMode mode) {
    this.voidMode = mode;
  }

  @Override
  public @Nonnull String getMachineName() {
    return "tank";
  }

  @Override
  public boolean isMachineItemValidForSlot(int i, @Nonnull ItemStack item) {
    if (canVoidItems() && voidMode == VoidMode.ALWAYS && i < getSlotDefinition().getMaxInputSlot()) {
      return false;
    }
    if (i == 0) {
      return FluidUtil.getFluidTypeFromItem(item) != null;
    } else if (i == 1) {
      return FluidUtil.hasEmptyCapacity(item) || canBeMended(item);
    } else if (i == 2 && canVoidItems()) {
      return voidMode == VoidMode.ALWAYS || (voidMode == VoidMode.NEVER ? false : !FluidUtil.isFluidContainer(item));
    }
    return false;
  }

  @Override
  public void setInventorySlotContents(int slot, @Nonnull ItemStack contents) {
    super.setInventorySlotContents(slot, contents);
  }

  @Override
  public boolean isActive() {
    return false;
  }

  private static long lastSendTickAll = -1;
  private long nextSendTickThis = -1;
  private int sendPrio = 0;

  /*
   * Limit sending of client updates because a group of tanks pushing into each other can severely kill the clients fps from doing these updates.
   */
  private boolean canSendClientUpdate() {
    long tick = EnderIO.proxy.getServerTickCount();
    if (nextSendTickThis > tick) {
      return false;
    }
    if (tick == lastSendTickAll && sendPrio++ < 200) {
      return false;
    }
    nextSendTickThis = (lastSendTickAll = tick) + 10 + sendPrio * 2;
    sendPrio = 0;
    return true;
  }

  @Override
  protected boolean processTasks(boolean redstoneCheck) {
    processItems(redstoneCheck);
    int filledLevel = getFilledLevel();
    if (lastUpdateLevel != filledLevel) {
      lastUpdateLevel = filledLevel;
      setTanksDirty();
    }
    if (tankDirty && canSendClientUpdate()) {
      PacketHandler.sendToAllAround(new PacketTankFluid(this), this);
      world.updateComparatorOutputLevel(pos, getBlockType());
      int thisFluidLuminosity = tank.getFluid() == null || tank.getFluid().getFluid() == null || tank.getFluidAmount() == 0 ? 0
          : tank.getFluid().getFluid().getLuminosity(tank.getFluid());
      if (thisFluidLuminosity != lastFluidLuminosity) {
        world.checkLight(getPos());
        world.markAndNotifyBlock(pos, world.getChunkFromBlockCoords(pos), world.getBlockState(pos), world.getBlockState(pos), 3);
        // world.notifyLightSet(getPos());
        // world.checkLightFor(EnumSkyBlock.BLOCK, pos);
        // updateBlock();
        lastFluidLuminosity = thisFluidLuminosity;
      }
      tankDirty = false;
    }
    return false;
  }

  public int getComparatorOutput() {
    if (tank.isEmpty()) {
      return 0;
    }

    return (int) (1 + ((double) tank.getFluidAmount() / (double) tank.getCapacity()) * 14);
  }

  private boolean processItems(boolean redstoneCheck) {
    if (!redstoneCheck) {
      return false;
    }
    if (!shouldDoWorkThisTick(20)) {
      return false;
    }
    if (Prep.isValid(getStackInSlot(2)) && canVoidItems()) {
      getStackInSlot(2).shrink(1);
      if (TankConfig.tankSmeltTrashIntoLava.get() && !tank.isFull() && tank.hasFluid(FluidRegistry.LAVA)) {
        tank.addFluidAmount(world.rand.nextInt(10) + 1);
      }
      SoundHelper.playSound(world, pos, SoundHelper.BLOCK_CENTER, SoundRegistry.ITEM_BURN, 0.4F, 2.0F + world.rand.nextFloat() * 0.4F);
      markDirty();
    }
    return drainFullContainer() || fillEmptyContainer() || mendItem();
  }

  private boolean canBeMended(ItemStack stack) {
    return stack != null && stack.isItemDamaged() && EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, stack) > 0
        && tank.hasFluid(Fluids.XP_JUICE.getFluid());
  }

  private boolean mendItem() {
    final int output = getSlotDefinition().getMaxOutputSlot();
    final int input = getSlotDefinition().getMinInputSlot() + 1;
    if (inventory[output] != null || inventory[input] == null || !inventory[input].isItemDamaged() || tank.isEmpty()
        || EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, inventory[input]) <= 0) {
      return false;
    }

    if (tank.getFluid().getFluid() != Fluids.XP_JUICE.getFluid()) {
      inventory[output] = inventory[input];
      inventory[input] = null;
      markDirty();
      return true;
    }

    int damageMendable = Math.min(xpToDurability(XpUtil.liquidToExperience(tank.getFluidAmount())), inventory[input].getItemDamage());
    if (damageMendable < 1) {
      return false;
    }
    inventory[input].setItemDamage(inventory[input].getItemDamage() - damageMendable);
    tank.drainInternal(XpUtil.experienceToLiquid(durabilityToXp(damageMendable)), true);
    ItemStack stack1 = inventory[input];

    if (!stack1.isItemDamaged()) {
      inventory[output] = inventory[input];
      inventory[input] = null;
    }

    markDirty();

    return true;
  }

  private int durabilityToXp(int durability) {
    return durability / 2;
  }

  private int xpToDurability(int xp) {
    return xp * 2;
  }

  private boolean fillEmptyContainer() {
    if (Prep.isInvalid(inventory[getSlotDefinition().getMinInputSlot() + 1]) || tank.isEmpty()) {
      return false;
    }

    FluidAndStackResult fill = FluidUtil.tryFillContainer(inventory[getSlotDefinition().getMinInputSlot() + 1], getOutputTanks()[0].getFluid());
    if (fill.result.fluidStack == null) {
      return false;
    }

    int slot = getSlotDefinition().getMaxOutputSlot();

    if (inventory[slot] != null) {
      if (inventory[slot].isStackable() && ItemUtil.areStackMergable(inventory[slot], fill.result.itemStack)
          && inventory[slot].getCount() < inventory[slot].getMaxStackSize()) {
        fill.result.itemStack.grow(inventory[slot].getCount());
      } else {
        return false;
      }
    }

    getOutputTanks()[0].setFluid(fill.remainder.fluidStack);
    setInventorySlotContents(getSlotDefinition().getMinInputSlot() + 1, fill.remainder.itemStack);
    setInventorySlotContents(slot, fill.result.itemStack);

    setTanksDirty();
    markDirty();
    return false;
  }

  private boolean drainFullContainer() {
    if (Prep.isInvalid(inventory[getSlotDefinition().getMinInputSlot()]) || tank.isFull()) {
      return false;
    }

    FluidAndStackResult fill = FluidUtil.tryDrainContainer(inventory[getSlotDefinition().getMinInputSlot()], this);
    if (fill.result.fluidStack == null) {
      return false;
    }

    int slot = getSlotDefinition().getMinOutputSlot();

    if (inventory[slot] != null && Prep.isValid(fill.result.itemStack)) {
      if (inventory[slot].isStackable() && ItemUtil.areStackMergable(inventory[slot], fill.result.itemStack)
          && inventory[slot].getCount() < inventory[slot].getMaxStackSize()) {
        fill.result.itemStack.grow(inventory[slot].getCount());
      } else {
        return false;
      }
    }

    getInputTank(fill.result.fluidStack).setFluid(fill.remainder.fluidStack);
    setInventorySlotContents(getSlotDefinition().getMinInputSlot(), fill.remainder.itemStack);
    if (Prep.isValid(fill.result.itemStack)) {
      setInventorySlotContents(slot, fill.result.itemStack);
    }

    setTanksDirty();
    markDirty();
    return false;
  }

  @Override
  public FluidTank getInputTank(FluidStack forFluidType) {
    return tank;
  }

  @Override
  public @Nonnull FluidTank[] getOutputTanks() {
    return new FluidTank[] { tank };
  }

  @Override
  public void setTanksDirty() {
    if (!tankDirty) {
      tankDirty = true;
      markDirty();
    }
  }

  @Override
  public boolean shouldRenderInPass(int pass) {
    return pass == 1 && tank.getFluidAmount() > 0;
  }

  @SuppressWarnings("null")
  @Override
  @Nonnull
  public List<ITankData> getTankDisplayData() {
    return Collections.<ITankData> singletonList(new ITankData() {

      @Override
      @Nonnull
      public EnumTankType getTankType() {
        return EnumTankType.STORAGE;
      }

      @Override
      @Nullable
      public FluidStack getContent() {
        return tank.getFluid();
      }

      @Override
      public int getCapacity() {
        return tank.getCapacity();
      }
    });
  }

  private SmartTankFluidHandler smartTankFluidHandler;

  protected SmartTankFluidHandler getSmartTankFluidHandler() {
    if (smartTankFluidHandler == null) {
      smartTankFluidHandler = new SmartTankFluidMachineHandler(this, tank);
    }
    return smartTankFluidHandler;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facingIn) {
    if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
      return (T) getSmartTankFluidHandler().get(facingIn);
    }
    return super.getCapability(capability, facingIn);
  }

}