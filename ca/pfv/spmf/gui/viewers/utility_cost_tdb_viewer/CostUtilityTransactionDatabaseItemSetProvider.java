package ca.pfv.spmf.gui.viewers.utility_cost_tdb_viewer;

import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator.ItemSetProvider;
import ca.pfv.spmf.input.cost_utility_transaction_database.CostUtilityTransactionDatabase;
import ca.pfv.spmf.input.cost_utility_transaction_database.ItemCost;
import ca.pfv.spmf.input.cost_utility_transaction_database.TransactionCost;
/*
 * Copyright (c) 2024 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 */
/**
 * Adapter class that allows CostUtilityTransactionDatabase to be used with ItemCooccurrenceHeatmapGenerator
 * @author Philippe Fournier-Viger
 */
class CostUtilityTransactionDatabaseItemSetProvider implements ItemSetProvider {
    
    private final CostUtilityTransactionDatabase db;
    
    public CostUtilityTransactionDatabaseItemSetProvider(CostUtilityTransactionDatabase db) {
        this.db = db;
    }
    
    @Override
    public int getItemSetCount() {
        return db.size();
    }
    
    @Override
    public List<Integer> getItemSet(int index) {
        TransactionCost transaction = db.getTransactions().get(index);
        List<Integer> items = new ArrayList<Integer>();
        for (ItemCost itemCost : transaction.getItems()) {
            items.add(itemCost.item);
        }
        return items;
    }
    
    @Override
    public String getItemName(int itemId) {
        return db.getNameForItem(itemId);
    }
}