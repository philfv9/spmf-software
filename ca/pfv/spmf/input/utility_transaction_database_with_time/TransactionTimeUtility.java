package ca.pfv.spmf.input.utility_transaction_database_with_time;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.List;

import ca.pfv.spmf.input.utility_transaction_database.ItemUtility;
import ca.pfv.spmf.input.utility_transaction_database.TransactionTP;

/**
 * This class represents a transaction (a set of items) from a transaction
 * database with utility values and time information.
 *
 * @see UtilityTimeTransactionDatabase
 * @see ItemUtility
 * 
 * @author Philippe Fournier-Viger
 */
public class TransactionTimeUtility extends TransactionTP {
	
	/** the transaction timestamp */
	protected final int timestamp;
	
	/**
	 * Constructor
	 * @param itemsUtilities list of items
	 * @param itemsUtilities list of corresponding utility values
	 * @param transactionUtility  the transaction utility
	 * @param timestamp a timestamp
	 */
	public TransactionTimeUtility(List<ItemUtility> itemsUtilities, int transactionUtility, Integer timestamp){
		super(itemsUtilities, transactionUtility);
		this.timestamp = timestamp;
	}
	
	/** Get the timestamp
	 * @return timestamp
	 */
	public int getTimeStamp() {
		return timestamp;
	}

}
