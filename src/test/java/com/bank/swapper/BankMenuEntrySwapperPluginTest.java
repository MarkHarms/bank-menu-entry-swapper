/*
 * Copyright (c) 2019, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.bank.swapper;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import org.mockito.Mock;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class BankMenuEntrySwapperPluginTest {
	private final static String AIR_RUNE = "Air rune";
	private final static String WATER_RUNE = "Water rune";
	
	@Mock
	@Bind
	Client client;
	
	@Mock
	@Bind
	ConfigManager configManager;
	
	@Mock
	@Bind
	ItemManager itemManager;
	
	@Mock
	@Bind
	ChatMessageManager chatMessageManager;
	
	@Mock
	@Bind
	Widget widget;
	
	@Mock
	@Bind
	BankMenuEntrySwapperConfig config;
	
	@Mock
	@Bind
	MenuEntry menuEntry;
	
	@Inject
	BankMenuEntrySwapperPlugin bankMenuEntrySwapperPlugin;
	
	private MenuEntry[] entries;
	private int param1;
	private boolean shiftStatus = true;
	
	@Before
	public void before() {
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
		
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getWidget(WidgetInfo.BANK_CONTAINER)).thenReturn(widget);
		when(bankMenuEntrySwapperPlugin.shiftModifier()).thenAnswer((Answer<Boolean>) invocationOnMock -> {
			return shiftStatus;
		});
		
		when(client.getMenuEntries()).thenAnswer((Answer<MenuEntry[]>) invocationOnMock -> {
			// The menu implementation returns a copy of the array, which causes swap() to not
			// modify the same array being iterated in onClientTick
			return Arrays.copyOf(entries, entries.length);
		});
		
		doAnswer((Answer<Void>) invocationOnMock -> {
			Object[] arguments = invocationOnMock.getArguments();
			when(configManager.getConfiguration((String)arguments[0], (String)arguments[1])).thenReturn(String.valueOf(arguments[2]));
			return null;
		}).when(configManager).setConfiguration(anyString(), anyString(), anyInt());
		
		when(client.createMenuEntry(anyInt())).thenAnswer((Answer<MenuEntry>) invocationOnMock -> {
			TestMenuEntry testMenuEntry = new TestMenuEntry();
			int index = invocationOnMock.getArgument(0);
			
			MenuEntry[] newEntries = new MenuEntry[entries.length + 1];
			for (int i = 0; i < entries.length + 1; i++) {
				if (i < index)
					newEntries[i] = entries[i];
				else if (i == index)
					newEntries[i] = testMenuEntry;
				else
					newEntries[i] = entries[i - 1];
			}
			entries = newEntries;
			return testMenuEntry;
		});
		
		doAnswer((Answer<Void>) invocationOnMock -> {
			Object argument = invocationOnMock.getArguments()[0];
			entries = (MenuEntry[]) argument;
			return null;
		}).when(client).setMenuEntries(any(MenuEntry[].class));
	}
	
	private MenuEntry menu(String option, String target, MenuAction menuAction) {
		return menu(option, target, menuAction, 0);
	}
	
	private MenuEntry menu(String option, String target, MenuAction menuAction, int identifier) {
		TestMenuEntry menuEntry = new TestMenuEntry();
		menuEntry.setOption(option);
		menuEntry.setTarget(target);
		menuEntry.setType(menuAction);
		menuEntry.setIdentifier(identifier);
		if (menuAction == MenuAction.RUNELITE) {
			menuEntry.setParam1(0);
		} else {
			menuEntry.setParam1(param1);
			menuEntry.setItemId(getItemId(target));
		}
		
		return menuEntry;
	}
	
	private int getItemId(String target) {
		int itemId = -1;
		if (AIR_RUNE.equals(target)) {
			itemId = 556;
		} else if (WATER_RUNE.equals(target)) {
			itemId = 557;
		}
		return itemId;
	}
	
	public void resetEntries(MenuEntry[] baseEntries, MenuEntry[] baseShiftEntries) {
		if (shiftStatus) {
			entries = baseShiftEntries;
		} else {
			entries = baseEntries;
		}
	}
	
	@Test
	public void testBankMenu() {
		lenient().when(config.bankCustomization()).thenReturn(true);
		
		param1 = 786445;
		ClientTick clientTick = new ClientTick();
		MenuOpened menuOpened = new MenuOpened();
		MenuEntry[] baseEntries;
		MenuEntry[] baseShiftEntries;
		
		baseEntries = new MenuEntry[] {
				menu("Cancel", "", MenuAction.CANCEL),
				menu("Examine", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-All-but-1", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-All", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-X", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-16", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-10", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-5", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-1", AIR_RUNE, MenuAction.CC_OP),
		};
		baseShiftEntries = baseEntries;
		
		
		resetEntries(baseEntries, baseShiftEntries);
		menuOpened.setMenuEntries(baseEntries);
		bankMenuEntrySwapperPlugin.onMenuOpened(menuOpened);
		
		MenuEntry[] newEntries = client.getMenuEntries();
		assertArrayEquals(new MenuEntry[] {
		/*  0 */menu("Cancel", "", MenuAction.CANCEL),
		/*  1 */menu("Reset swap", AIR_RUNE, MenuAction.RUNELITE),
		/*  2 */menu("Swap shift click Withdraw-All-but-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  3 */menu("Swap shift click Withdraw-All", AIR_RUNE, MenuAction.RUNELITE),
		/*  4 */menu("Swap shift click Withdraw-X", AIR_RUNE, MenuAction.RUNELITE),
		/*  5 */menu("Swap shift click Withdraw-16", AIR_RUNE, MenuAction.RUNELITE),
		/*  6 */menu("Swap shift click Withdraw-10", AIR_RUNE, MenuAction.RUNELITE),
		/*  7 */menu("Swap shift click Withdraw-5", AIR_RUNE, MenuAction.RUNELITE),
		/*  8 */menu("Swap shift click Withdraw-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  9 */menu("Swap left click Withdraw-All-but-1", AIR_RUNE, MenuAction.RUNELITE),
		/* 10 */menu("Swap left click Withdraw-All", AIR_RUNE, MenuAction.RUNELITE),
		/* 11 */menu("Swap left click Withdraw-X", AIR_RUNE, MenuAction.RUNELITE),
		/* 12 */menu("Swap left click Withdraw-16", AIR_RUNE, MenuAction.RUNELITE),
		/* 13 */menu("Swap left click Withdraw-10", AIR_RUNE, MenuAction.RUNELITE),
		/* 14 */menu("Swap left click Withdraw-5", AIR_RUNE, MenuAction.RUNELITE),
		/* 15 */menu("Swap left click Withdraw-1", AIR_RUNE, MenuAction.RUNELITE),
		/* 16 */menu("Examine", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 17 */menu("Withdraw-All-but-1", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 18 */menu("Withdraw-All", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 19 */menu("Withdraw-X", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 20 */menu("Withdraw-16", AIR_RUNE, MenuAction.CC_OP),
		/* 21 */menu("Withdraw-10", AIR_RUNE, MenuAction.CC_OP),
		/* 22 */menu("Withdraw-5", AIR_RUNE, MenuAction.CC_OP),
		/* 23 */menu("Withdraw-1", AIR_RUNE, MenuAction.CC_OP),
		}, newEntries);
		
		
		((TestMenuEntry)newEntries[7]).getClickConsumer().accept(menuEntry);
		resetEntries(baseEntries, baseShiftEntries);
		bankMenuEntrySwapperPlugin.onClientTick(clientTick);
		
		ArgumentCaptor<MenuEntry[]> argumentCaptor = ArgumentCaptor.forClass(MenuEntry[].class);
		verify(client, times(1)).setMenuEntries(argumentCaptor.capture());
		
		baseShiftEntries = argumentCaptor.getValue();
		assertArrayEquals(new MenuEntry[] {
				menu("Cancel", "", MenuAction.CANCEL),
				menu("Examine", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-All-but-1", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-All", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-X", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-16", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-10", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-1", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-5", AIR_RUNE, MenuAction.CC_OP),
		}, baseShiftEntries);
		
		
		shiftStatus = false;
		resetEntries(baseEntries, baseShiftEntries);
		bankMenuEntrySwapperPlugin.onClientTick(clientTick);
		verify(client, times(1)).setMenuEntries(argumentCaptor.capture()); // verify it wasn't called.
		
		
		shiftStatus = true;
		resetEntries(baseEntries, baseShiftEntries);
		menuOpened.setMenuEntries(baseShiftEntries);
		bankMenuEntrySwapperPlugin.onMenuOpened(menuOpened);
		shiftStatus = false;
		
		newEntries = client.getMenuEntries();
		assertArrayEquals(new MenuEntry[] {
		/*  0 */menu("Cancel", "", MenuAction.CANCEL),
		/*  1 */menu("Reset swap", AIR_RUNE, MenuAction.RUNELITE),
		/*  2 */menu("Swap shift click Withdraw-All-but-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  3 */menu("Swap shift click Withdraw-All", AIR_RUNE, MenuAction.RUNELITE),
		/*  4 */menu("Swap shift click Withdraw-X", AIR_RUNE, MenuAction.RUNELITE),
		/*  5 */menu("Swap shift click Withdraw-16", AIR_RUNE, MenuAction.RUNELITE),
		/*  6 */menu("Swap shift click Withdraw-10", AIR_RUNE, MenuAction.RUNELITE),
		/*  7 */menu("Swap shift click Withdraw-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  8 */menu("Swap left click Withdraw-All-but-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  9 */menu("Swap left click Withdraw-All", AIR_RUNE, MenuAction.RUNELITE),
		/* 10 */menu("Swap left click Withdraw-X", AIR_RUNE, MenuAction.RUNELITE),
		/* 11 */menu("Swap left click Withdraw-16", AIR_RUNE, MenuAction.RUNELITE),
		/* 12 */menu("Swap left click Withdraw-10", AIR_RUNE, MenuAction.RUNELITE),
		/* 13 */menu("Swap left click Withdraw-1", AIR_RUNE, MenuAction.RUNELITE),
		/* 14 */menu("Swap left click Withdraw-5", AIR_RUNE, MenuAction.RUNELITE),
		/* 15 */menu("Examine", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 16 */menu("Withdraw-All-but-1", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 17 */menu("Withdraw-All", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 18 */menu("Withdraw-X", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 19 */menu("Withdraw-16", AIR_RUNE, MenuAction.CC_OP),
		/* 20 */menu("Withdraw-10", AIR_RUNE, MenuAction.CC_OP),
		/* 21 */menu("Withdraw-1", AIR_RUNE, MenuAction.CC_OP),
		/* 22 */menu("Withdraw-5", AIR_RUNE, MenuAction.CC_OP),
		}, newEntries);
		
		
		((TestMenuEntry)newEntries[12]).getClickConsumer().accept(menuEntry);
		resetEntries(baseEntries, baseShiftEntries);
		bankMenuEntrySwapperPlugin.onClientTick(clientTick);
		
		argumentCaptor = ArgumentCaptor.forClass(MenuEntry[].class);
		verify(client, times(2)).setMenuEntries(argumentCaptor.capture());
		
		assertArrayEquals(new MenuEntry[] {
				menu("Cancel", "", MenuAction.CANCEL),
				menu("Examine", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-All-but-1", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-All", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-X", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-16", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-1", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-5", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-10", AIR_RUNE, MenuAction.CC_OP),
		}, argumentCaptor.getValue());
		
		
		shiftStatus = true;
		resetEntries(baseEntries, baseShiftEntries);
		menuOpened.setMenuEntries(baseShiftEntries);
		bankMenuEntrySwapperPlugin.onMenuOpened(menuOpened);
		
		newEntries = client.getMenuEntries();
		assertArrayEquals(new MenuEntry[] {
		/*  0 */menu("Cancel", "", MenuAction.CANCEL),
		/*  1 */menu("Reset swap", AIR_RUNE, MenuAction.RUNELITE),
		/*  2 */menu("Swap shift click Withdraw-All-but-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  3 */menu("Swap shift click Withdraw-All", AIR_RUNE, MenuAction.RUNELITE),
		/*  4 */menu("Swap shift click Withdraw-X", AIR_RUNE, MenuAction.RUNELITE),
		/*  5 */menu("Swap shift click Withdraw-16", AIR_RUNE, MenuAction.RUNELITE),
		/*  6 */menu("Swap shift click Withdraw-10", AIR_RUNE, MenuAction.RUNELITE),
		/*  7 */menu("Swap shift click Withdraw-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  8 */menu("Swap left click Withdraw-All-but-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  9 */menu("Swap left click Withdraw-All", AIR_RUNE, MenuAction.RUNELITE),
		/* 10 */menu("Swap left click Withdraw-X", AIR_RUNE, MenuAction.RUNELITE),
		/* 11 */menu("Swap left click Withdraw-16", AIR_RUNE, MenuAction.RUNELITE),
		/* 12 */menu("Swap left click Withdraw-1", AIR_RUNE, MenuAction.RUNELITE),
		/* 13 */menu("Swap left click Withdraw-5", AIR_RUNE, MenuAction.RUNELITE),
		/* 14 */menu("Examine", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 15 */menu("Withdraw-All-but-1", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 16 */menu("Withdraw-All", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 17 */menu("Withdraw-X", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 18 */menu("Withdraw-16", AIR_RUNE, MenuAction.CC_OP),
		/* 19 */menu("Withdraw-10", AIR_RUNE, MenuAction.CC_OP),
		/* 20 */menu("Withdraw-1", AIR_RUNE, MenuAction.CC_OP),
		/* 21 */menu("Withdraw-5", AIR_RUNE, MenuAction.CC_OP),
		}, newEntries);
		
		
		((TestMenuEntry)newEntries[6]).getClickConsumer().accept(menuEntry);
		entries = baseEntries;
		bankMenuEntrySwapperPlugin.onClientTick(clientTick);
		
		argumentCaptor = ArgumentCaptor.forClass(MenuEntry[].class);
		verify(client, times(3)).setMenuEntries(argumentCaptor.capture());
		
		baseShiftEntries = argumentCaptor.getValue();
		assertArrayEquals(new MenuEntry[] {
				menu("Cancel", "", MenuAction.CANCEL),
				menu("Examine", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-All-but-1", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-All", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-X", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
				menu("Withdraw-16", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-1", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-5", AIR_RUNE, MenuAction.CC_OP),
				menu("Withdraw-10", AIR_RUNE, MenuAction.CC_OP),
		}, baseShiftEntries);
		
		
		resetEntries(baseEntries, baseShiftEntries);
		menuOpened.setMenuEntries(baseShiftEntries);
		bankMenuEntrySwapperPlugin.onMenuOpened(menuOpened);
		
		newEntries = client.getMenuEntries();
		assertArrayEquals(new MenuEntry[] {
		/*  0 */menu("Cancel", "", MenuAction.CANCEL),
		/*  1 */menu("Reset swap", AIR_RUNE, MenuAction.RUNELITE),
		/*  2 */menu("Swap shift click Withdraw-All-but-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  3 */menu("Swap shift click Withdraw-All", AIR_RUNE, MenuAction.RUNELITE),
		/*  4 */menu("Swap shift click Withdraw-X", AIR_RUNE, MenuAction.RUNELITE),
		/*  5 */menu("Swap shift click Withdraw-16", AIR_RUNE, MenuAction.RUNELITE),
		/*  6 */menu("Swap shift click Withdraw-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  7 */menu("Swap shift click Withdraw-5", AIR_RUNE, MenuAction.RUNELITE),
		/*  8 */menu("Swap left click Withdraw-All-but-1", AIR_RUNE, MenuAction.RUNELITE),
		/*  9 */menu("Swap left click Withdraw-All", AIR_RUNE, MenuAction.RUNELITE),
		/* 10 */menu("Swap left click Withdraw-X", AIR_RUNE, MenuAction.RUNELITE),
		/* 11 */menu("Swap left click Withdraw-16", AIR_RUNE, MenuAction.RUNELITE),
		/* 12 */menu("Swap left click Withdraw-1", AIR_RUNE, MenuAction.RUNELITE),
		/* 13 */menu("Swap left click Withdraw-5", AIR_RUNE, MenuAction.RUNELITE),
		/* 14 */menu("Examine", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 15 */menu("Withdraw-All-but-1", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 16 */menu("Withdraw-All", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 17 */menu("Withdraw-X", AIR_RUNE, MenuAction.CC_OP_LOW_PRIORITY),
		/* 18 */menu("Withdraw-16", AIR_RUNE, MenuAction.CC_OP),
		/* 19 */menu("Withdraw-1", AIR_RUNE, MenuAction.CC_OP),
		/* 20 */menu("Withdraw-5", AIR_RUNE, MenuAction.CC_OP),
		/* 21 */menu("Withdraw-10", AIR_RUNE, MenuAction.CC_OP),
		}, newEntries);
		
		// TODO: Multiple different items? Test that bank and inventory dont impact eachother?
	}
}