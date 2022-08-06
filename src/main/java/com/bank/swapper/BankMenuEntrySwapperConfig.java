package com.bank.swapper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BankMenuEntrySwapperConfig.GROUP)
public interface BankMenuEntrySwapperConfig extends Config
{
	String GROUP = "bankmenuentryswapper";
	
	@ConfigItem(
		keyName = "bankClickCustomization",
		name = "Bank menu swapping",
		description = "Allows customization of left/shift-clicks on bank items",
		position = 1
	)
	default boolean bankCustomization()
	{
		return true;
	}
	
	
	
	@ConfigItem(
		keyName = "bankInventoryClickCustomization",
		name = "Bank inventory menu swapping",
		description = "Allows customization of left/shift-clicks on inventory items in bank interface",
		position = 2
	)
	default boolean bankInventoryCustomization()
	{
		return true;
	}
}