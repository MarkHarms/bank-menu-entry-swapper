package com.bank.swapper;

import com.google.inject.Provides;

import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(name = "Bank Menu Entry Swapper")
public class BankMenuEntrySwapperPlugin extends Plugin {
	static final String BANK_KEY_PREFIX = "bank_";
	static final String BANK_SHIFT_KEY_PREFIX = "bank_shift_";
	static final String BANK_INVENTORY_KEY_PREFIX = "bank_inventory_";
	static final String BANK_INVENTORY_SHIFT_KEY_PREFIX = "bank_inventory_shift_";
	
	private static final Set<MenuAction> menuTypes = Set.of(MenuAction.CC_OP, MenuAction.CC_OP_LOW_PRIORITY);
	private static final Set<Integer> bankWidgetIds = Set.of(WidgetID.BANK_GROUP_ID/*, WidgetID.GROUP_STORAGE_GROUP_ID*/);
	private static final Set<Integer> bankInventoryWidgetIds = Set.of(WidgetID.BANK_INVENTORY_GROUP_ID/*, WidgetID.GROUP_STORAGE_INVENTORY_GROUP_ID*/);
	
	@Inject
	private Client client;
	
	@Inject
	private BankMenuEntrySwapperConfig config;
	
	@Inject
	private ConfigManager configManager;
	
	@Inject
	private ChatMessageManager chatMessageManager;
	
	@Provides
	BankMenuEntrySwapperConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(BankMenuEntrySwapperConfig.class);
	}
	
	@Override
	protected void startUp() throws Exception {
		log.info("BMES started.");
	}
	
	@Override
	protected void shutDown() throws Exception {
		log.info("BMES shut down.");
	}
	
	@Subscribe
	public void onClientTick(ClientTick clientTick) {
		// The menu is not rebuilt when it is open, so don't swap or else it will
		// repeatedly swap entries
		if (client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen()) {
			return;
		}
		
		MenuEntry[] menuEntries = client.getMenuEntries();
		
		// Perform swaps
		int idx = 0;
		for (MenuEntry entry : menuEntries) {
			swapMenuEntry(menuEntries, idx++, entry);
		}
	}
	
	private void swapMenuEntry(MenuEntry[] menuEntries, int index, MenuEntry menuEntry) {
		Boolean inventory = null;
		if (isCorrectWidget(menuEntry, false) && config.bankCustomization()) {
			inventory = false;
		}
		if (isCorrectWidget(menuEntry, true) && config.bankInventoryCustomization()) {
			inventory = true;
		}
		
		if (inventory != null) {
			String configString = (shiftModifier()) ? getShiftString(inventory) : getLeftString(inventory);
			Integer customOption = getSwapConfig(configString, menuEntry.getItemId());
			
			if (customOption != null && customOption.equals(index)) {
				MenuEntry temp = menuEntries[index];
				temp.setType(MenuAction.CC_OP);
				menuEntries[index] = menuEntries[menuEntries.length - 1];
				menuEntries[menuEntries.length - 1] = temp;
				
				client.setMenuEntries(menuEntries);
			}
		}
	}
	
	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		if (client.getWidget(WidgetInfo.BANK_CONTAINER) != null || client.getWidget(WidgetInfo.GROUP_STORAGE_ITEM_CONTAINER) != null) {
			configureClick(event, false);
			configureClick(event, true);
		}
	}
	
	private boolean isCorrectWidget(MenuEntry entry, boolean inventory) {
		boolean correctWidget;
		
		final int widgetGroupId = WidgetInfo.TO_GROUP(entry.getParam1());
		if (inventory) {
			correctWidget = bankInventoryWidgetIds.contains(widgetGroupId) && !entry.getOption().equalsIgnoreCase("examine");
		} else {
			correctWidget = bankWidgetIds.contains(widgetGroupId) && menuTypes.contains(entry.getType()) && !entry.getOption().equalsIgnoreCase("examine");
		}
		return correctWidget;
	}
	
	private void configureClick(MenuOpened event, boolean inventory) {
		if (!shiftModifier() || (!inventory && !config.bankCustomization()) || (inventory && !config.bankInventoryCustomization())) {
			return;
		}
		String leftString = getLeftString(inventory);
		String shiftString = getShiftString(inventory);
		
		MenuEntry[] entries = event.getMenuEntries();
		
		MenuEntry topEntry = entries[entries.length - 1];
		int ignoreIndex = getIgnoreIndex(entries.length, getSwapConfig(shiftString, topEntry.getItemId()), topEntry.getOption());
		
		Integer leftIndex = getSwapConfig(leftString, topEntry.getItemId());
		Integer shiftIndex = getSwapConfig(shiftString, topEntry.getItemId());
		if (shiftIndex != null && leftIndex != null && shiftIndex == leftIndex) {
			leftIndex = entries.length - 1;
		}
		String shiftOption = (shiftIndex == null) ? null : entries[entries.length - 1].getOption();
		String leftOption = (leftIndex == null) ? null : entries[leftIndex].getOption();
		
		String lastTarget = null;
		MenuEntry lastEntry = null;
		for (int idx = entries.length - 1; idx >= 0; --idx) {
			
			MenuEntry entry = entries[idx];
			if (ignoreIndex == idx && !entry.getOption().endsWith("-1")) {
				continue;
			}
			boolean matchesLeft = entry.getOption().equals(leftOption);
			boolean matchesShift = entry.getOption().equals(shiftOption);
			
			if (isCorrectWidget(entry, inventory)) {
				if (leftIndex == null || !matchesLeft) {
					int passedIndex = (matchesShift) ? shiftIndex : idx;
					buildMenuEntry("Swap left click " + entry.getOption(), entry.getTarget(), setConfig(leftString, entry, passedIndex, false));
				}
				
				if (shiftIndex == null || !matchesShift) {
					int passedIndex = (shiftIndex != null && shiftIndex == idx) ? entries.length - 1 : idx;
					buildMenuEntry("Swap shift click " + entry.getOption(), entry.getTarget(), setConfig(shiftString, entry, passedIndex, true));
				}
				lastTarget = entry.getTarget();
				lastEntry = entry;
			}
		}
		if (lastEntry != null) {
			buildMenuEntry("Reset swap", lastTarget, unsetConfig(leftString, shiftString, lastEntry));
		}
	}
	
	private MenuEntry buildMenuEntry(String option, String target, Consumer<MenuEntry> setter) {
		return client.createMenuEntry(0)
				.setOption(option)
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(setter);
	}
	
	private Consumer<MenuEntry> setConfig(String key, MenuEntry entry, int menuIdx, boolean shift) {
		return e -> {
			final String message = new ChatMessageBuilder()
					.append("The default ").append(shift ? "shift" : "left").append(" click option for '").append(Text.removeTags(entry.getTarget())).append("' ")
					.append("has been set to '").append(entry.getOption()).append("'.")
					.build();
			
			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(message)
					.build());
			
			setSwapConfig(key, entry.getItemId(), menuIdx);
		};
	}
	
	private Consumer<MenuEntry> unsetConfig(String leftKey, String shiftKey, MenuEntry entry) {
		return e -> {
			final String message = new ChatMessageBuilder()
					.append("The default left and shift click options for '").append(Text.removeTags(entry.getTarget())).append("' ")
					.append("have been reset.")
					.build();
			
			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(message)
					.build());
			
			unsetSwapConfig(leftKey, entry.getItemId());
			unsetSwapConfig(shiftKey, entry.getItemId());
		};
	}
	
	private Integer getSwapConfig(String key, int itemId) {
		
		itemId = ItemVariationMapping.map(itemId);
		
		String config = configManager.getConfiguration(BankMenuEntrySwapperConfig.GROUP, key + itemId);
		
		return (config != null) ? Integer.parseInt(config) : null;
	}
	
	private void setSwapConfig(String key, int itemId, int index) {
		itemId = ItemVariationMapping.map(itemId);
		
		configManager.setConfiguration(BankMenuEntrySwapperConfig.GROUP, key + itemId, index);
	}
	
	private void unsetSwapConfig(String key, int itemId) {
		itemId = ItemVariationMapping.map(itemId);
		
		configManager.unsetConfiguration(BankMenuEntrySwapperConfig.GROUP, key + itemId);
	}
	
	private int getIgnoreIndex(int size, Integer storedIndex, String option) {
		return (storedIndex != null) ? storedIndex : size - 1;
	}
	
	private String getLeftString(boolean inventory) {
		return (inventory) ? BANK_INVENTORY_KEY_PREFIX : BANK_KEY_PREFIX;
	}
	
	private String getShiftString(boolean inventory) {
		return (inventory) ? BANK_INVENTORY_SHIFT_KEY_PREFIX : BANK_SHIFT_KEY_PREFIX;
	}
	
	boolean shiftModifier() {
		return client.isKeyPressed(KeyCode.KC_SHIFT);
	}
	
}
