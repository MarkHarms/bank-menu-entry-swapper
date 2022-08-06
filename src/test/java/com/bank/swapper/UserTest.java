package com.bank.swapper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class UserTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BankMenuEntrySwapperPlugin.class);
		RuneLite.main(args);
	}
}