package crazypants.enderio.powertools.config;

import crazypants.enderio.base.config.factory.IValue;
import crazypants.enderio.base.config.factory.ValueFactory;

public final class PersonalConfig {

  public static final ValueFactory F = Config.F.section("personal");

  public static final IValue<Boolean> capacitorBankRenderPowerOverlayOnItem = F.make("capacitorBankRenderPowerOverlayOnItem", false, //
      "When true, the capacitor bank item will get a power bar in addition to the gauge on the bank.");

}
