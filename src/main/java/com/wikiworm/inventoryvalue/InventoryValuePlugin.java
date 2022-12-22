/*
 *  BSD 2-Clause License
 *
 *  Copyright (c) 2020, wikiworm (Brandon Ripley)
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 *  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 *  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wikiworm.inventoryvalue;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.http.api.item.ItemPrice;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.LongStream;

import static net.runelite.api.ItemID.*;

@PluginDescriptor(name = "Inventory Value")
@Slf4j
public class InventoryValuePlugin extends Plugin
{
    private static final int NUM_SLOTS = 4;
    private static final int[] AMOUNT_VARBITS = {
            Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4
    };
    private static final int[] RUNE_VARBITS = {
            Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3, Varbits.RUNE_POUCH_RUNE4
    };

    @Inject
    private Client client;

    @Inject
    private InventoryValueConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private InventoryValueOverlay overlay;

    private HashMap<ItemPrice, Integer> _herbs  = new HashMap<ItemPrice, Integer>();
    private HashMap<ItemPrice, Integer> _gems   = new HashMap<ItemPrice, Integer>();
    private HashMap<ItemPrice, Integer> _seeds  = new HashMap<ItemPrice, Integer>();
    private long _oldInventoryValue     = Long.MIN_VALUE;
    private long _oldProfitInvValue     = Long.MIN_VALUE;
    private long _originalBankValue     = Long.MIN_VALUE;
    private long _lastBankValue         = Long.MIN_VALUE;

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        long inventoryValue, bankValue, profitInvValue, profitBankValue;
        inventoryValue = profitInvValue = profitBankValue = 0;
        final List<String> ignoredItems = buildIgnoredItemsList();

        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container != null) {
            Item[] items = container.getItems();
            inventoryValue = Arrays.stream(items).flatMapToLong(item ->
                    LongStream.of(calculateItemValue(item, ignoredItems))).sum();
            inventoryValue += handleHerbSack();
            inventoryValue += handleSeedBox();
            inventoryValue += handleGemBag();
        }
        if(_oldProfitInvValue == Long.MIN_VALUE) profitInvValue = 0;
        else if(event.getContainerId() != InventoryID.BANK.getId()) {
            // subtract the old inventory value from the latest inventory value then and add our existing profit
            profitInvValue = (inventoryValue - _oldInventoryValue) + _oldProfitInvValue;
        }

        // check if we're banking...
        if(event.getContainerId() == InventoryID.BANK.getId()) {
            container = client.getItemContainer(InventoryID.BANK);
            if (container != null) {
                Item[] items = container.getItems();
                bankValue = Arrays.stream(items).flatMapToLong(item ->
                        LongStream.of(calculateItemValue(item, ignoredItems))).sum();

                if(_originalBankValue == Long.MIN_VALUE) _originalBankValue = bankValue;

                profitBankValue = bankValue - _originalBankValue;
                _lastBankValue = bankValue;
            }
        } else {
            profitBankValue = _lastBankValue - _originalBankValue;
        }

        overlay.updateInventoryValue(inventoryValue, profitInvValue, profitBankValue);
        _oldInventoryValue = inventoryValue;
        _oldProfitInvValue = profitInvValue;
    }



    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getId() == 6 && (event.getItemId() == OPEN_HERB_SACK || event.getItemId() == HERB_SACK)) {
            _herbs.clear();
        }

        if (event.getId() == 6 && (event.getItemId() == OPEN_SEED_BOX || event.getItemId() == SEED_BOX)) {
            _seeds.clear();
        }

        if (event.getId() == 6 && (event.getItemId() == OPEN_GEM_BAG || event.getItemId() == GEM_BAG)) {
            _seeds.clear();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        String messageString = chatMessage.getMessage();
        if(messageString.contains("x Grimy"))
        {
            // split into quantity and herb name
            String[] qtyAndHerb = chatMessage.getMessage().split(" x ");
            if(qtyAndHerb.length == 2)
            {
                String herbName = qtyAndHerb[1].trim();
                int herbQty = Integer.parseInt(qtyAndHerb[0].trim(), 10);
                List<ItemPrice> itemPrices = itemManager.search(herbName);
                if(itemPrices.size() == 1) {
                    ItemPrice price = itemPrices.get(0);
                    _herbs.put(price, herbQty);
                }
            }
        }

        if(messageString.compareTo("The herb sack is empty.") == 0)
        {
            _herbs.clear();
        }
    }

    public List<String> buildIgnoredItemsList() {
        List<String> ignoredItemsList = Arrays.asList(config.ignoreItems().toLowerCase().split("[,;]"));
        ignoredItemsList.replaceAll(String::trim);
        return ignoredItemsList;
    }

    public long calculateItemValue(Item item, List<String> ignoredItems) {
        int itemId = item.getId();
        if(itemManager != null) {
            if (itemId == ItemID.RUNE_POUCH || itemId == ItemID.RUNE_POUCH_L
                    || itemId == ItemID.DIVINE_RUNE_POUCH || itemId == ItemID.DIVINE_RUNE_POUCH_L)
            {
                return handleRunePouch(item);
            }
            ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            String itemName = itemComposition.getName();

            if ((itemId == ItemID.COINS_995 && config.ignoreCoins())) {
                return 0L;
            } else if(itemId == ItemID.COINS_995) {
                return item.getQuantity();
            } else if (ignoredItems.contains(itemName.toLowerCase())) {
                return 0L;
            }

            return (long) item.getQuantity() * (config.useHighAlchemyValue() ?
                    itemComposition.getHaPrice() : itemManager.getItemPrice(itemId));
        } else {
            return 0L;
        }
    }

    @Provides
    InventoryValueConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(InventoryValueConfig.class);
    }

    private long handleRunePouch(Item runePouch) {
        final EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
        int num = 0;
        long totalValue = 0L;
        for (int i = 0; i < NUM_SLOTS; i++)
        {
            @Varbit int amountVarbit = AMOUNT_VARBITS[i];
            int amount = client.getVarbitValue(amountVarbit);

            @Varbit int runeVarbit = RUNE_VARBITS[i];
            int runeId = client.getVarbitValue(runeVarbit);
            int itemId = runepouchEnum.getIntValue(runeId);
            ItemComposition itemComposition = itemManager.getItemComposition(itemId);

            totalValue += (long) amount * (config.useHighAlchemyValue() ?
                    itemComposition.getHaPrice() : itemManager.getItemPrice(itemId));
        }
        return totalValue;
    }

    public long handleHerbSack() {
        long herbSackValue = 0L;
        for(Map.Entry<ItemPrice,Integer> entry : _herbs.entrySet()) {
            ItemComposition itemComposition = itemManager.getItemComposition(entry.getKey().getId());
            herbSackValue += (long) entry.getValue() * (config.useHighAlchemyValue() ?
                    itemComposition.getHaPrice() : entry.getKey().getPrice());
        }
        return herbSackValue;
    }

    public long handleGemBag() {
        long gemBagValue = 0L;
        for(Map.Entry<ItemPrice,Integer> entry : _gems.entrySet()) {
            ItemComposition itemComposition = itemManager.getItemComposition(entry.getKey().getId());
            gemBagValue += (long) entry.getValue() * (config.useHighAlchemyValue() ?
                    itemComposition.getHaPrice() : entry.getKey().getPrice());
        }
        return gemBagValue;
    }

    public long handleSeedBox() {
        long seedBoxValue = 0L;
        for(Map.Entry<ItemPrice,Integer> entry : _seeds.entrySet()) {
            ItemComposition itemComposition = itemManager.getItemComposition(entry.getKey().getId());
            seedBoxValue += (long) entry.getValue() * (config.useHighAlchemyValue() ?
                    itemComposition.getHaPrice() : entry.getKey().getPrice());
        }
        return seedBoxValue;
    }
}