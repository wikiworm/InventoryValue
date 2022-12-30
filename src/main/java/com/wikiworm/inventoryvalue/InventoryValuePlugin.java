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

    private HashMap<String, ItemPrice> _gemLookup = new HashMap<>();

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);

        _gemLookup.put("Sapphires",itemManager.search("Uncut sapphire").get(0));
        _gemLookup.put("Emeralds",itemManager.search("Uncut emerald").get(0));
        _gemLookup.put("Rubies",itemManager.search("Uncut ruby").get(0));
        _gemLookup.put("Diamonds",itemManager.search("Uncut diamond").get(0));
        _gemLookup.put("Dragonstones",itemManager.search("Uncut dragonstone").get(0));
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
        updateInventoryValue(event);
    }

    public void updateInventoryValue(ItemContainerChanged event) {
        boolean banking = event.getContainerId() == InventoryID.BANK.getId();
        updateInventoryValue(banking);
    }

    public void updateInventoryValue(boolean banking) {
        long inventoryValue, bankValue, profitInvValue, profitBankValue;
        inventoryValue = profitInvValue = profitBankValue = 0;
        final List<String> ignoredItems = buildIgnoredItemsList();

        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container != null) {
            Item[] items = container.getItems();
            inventoryValue = Arrays.stream(items).flatMapToLong(item ->
                    LongStream.of(calculateItemValue(item, ignoredItems))).sum();
        }
        if(_oldProfitInvValue == Long.MIN_VALUE) profitInvValue = 0;
        else if(!banking) {
            // subtract the old inventory value from the latest inventory value then and add our existing profit
            profitInvValue = (inventoryValue - _oldInventoryValue) + _oldProfitInvValue;
        }

        // check if we're banking...
        if(banking) {
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
        // check option
        if (event.getId() == 6 && (event.getItemId() == OPEN_HERB_SACK || event.getItemId() == HERB_SACK)) {
            _herbs.clear();
        }

        if (event.getId() == 6 && (event.getItemId() == OPEN_SEED_BOX || event.getItemId() == SEED_BOX)) {
            _seeds.clear();
        }

        if (event.getId() == 6 && (event.getItemId() == OPEN_GEM_BAG || event.getItemId() == GEM_BAG)) {
            _gems.clear();
        }

        // empty option
        if (event.getId() == 4 && (event.getItemId() == OPEN_HERB_SACK || event.getItemId() == HERB_SACK)) {
            _herbs.clear();
        }

        if (event.getId() == 4 && (event.getItemId() == OPEN_SEED_BOX || event.getItemId() == SEED_BOX)) {
            _seeds.clear();
        }

        if (event.getId() == 4 && (event.getItemId() == OPEN_GEM_BAG || event.getItemId() == GEM_BAG)) {
            _gems.clear();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        String messageString = chatMessage.getMessage();

        // if this is a chat message for a herb sack "check"
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
                    updateInventoryValue(false);
                }
            }
        }

        // if this is a chat message for a gem bag "check"
        if(messageString.contains("Sapphires:") && messageString.contains("Emeralds:")) {
            List<String> keys = new ArrayList<String>(Arrays.asList(messageString.split("\\P{L}+")));
            List<String> values = new ArrayList<String>(Arrays.asList(messageString.split("[^0-9]+")));
            keys.removeIf((str)-> str.compareTo("br") == 0);
            values.removeIf(String::isEmpty);
            if(keys.size() == values.size()) {
                for(int i = 0; i < keys.size(); i++) {
                    ItemPrice price = _gemLookup.get(keys.get(i));
                    _gems.put(price, Integer.parseInt(values.get(i)));
                    updateInventoryValue(false);
                }
            }

        }

        // if this is a chat message for a seed box "check"
        if(messageString.contains(" x ") && messageString.contains(" seed."))
        {
            // split into quantity and herb name
            String[] qtyAndSeed = chatMessage.getMessage().split(" x ");
            if(qtyAndSeed.length == 2)
            {
                // cut off the period...
                String seedName = qtyAndSeed[1].trim().replace(".","");
                int seedQty = Integer.parseInt(qtyAndSeed[0].trim(), 10);
                List<ItemPrice> itemPrices = itemManager.search(seedName);
                if(itemPrices.size() == 1) {
                    ItemPrice price = itemPrices.get(0);
                    _seeds.put(price, seedQty);
                    updateInventoryValue(false);
                }
            }
        }

        if(messageString.compareTo("The herb sack is empty.") == 0)
        {
            _herbs.clear();
        }

        if(messageString.compareTo("The gem bag is empty.") == 0)
        {
            _gems.clear();
        }

        if(messageString.compareTo("The seed box is empty.") == 0)
        {
            _seeds.clear();
        }

        if(messageString.compareTo("!Reset_iv") == 0) {
            _oldInventoryValue  = 0L;
            _oldProfitInvValue  = 0L;
            _lastBankValue      = 0L;
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
            } else if(itemId == ItemID.GEM_BAG_12020 ||itemId == ItemID.OPEN_GEM_BAG) {
                return handleGemBag();
            } else if (itemId == ItemID.HERB_SACK || itemId == ItemID.OPEN_HERB_SACK) {
                return handleHerbSack();
            } else if (itemId == ItemID.SEED_BOX || itemId == ItemID.OPEN_SEED_BOX) {
                return handleSeedBox();
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