package ca.pfv.spmf.gui.viewers.transactionviewer;

import java.util.List;

import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator.ItemSetProvider;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;
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
 * Adapter class that allows TransactionDatabase to be used with ItemCooccurrenceHeatmapGenerator
 * @author Philippe Fournier-Viger
 */
class TransactionDatabaseItemSetProvider implements ItemSetProvider {
    
    private final TransactionDatabase db;
    
    public TransactionDatabaseItemSetProvider(TransactionDatabase db) {
        this.db = db;
    }
    
    @Override
    public int getItemSetCount() {
        return db.size();
    }
    
    @Override
    public List<Integer> getItemSet(int index) {
        return db.getTransactions().get(index);
    }
    
    @Override
    public String getItemName(int itemId) {
        return db.getNameForItem(itemId);
    }
}