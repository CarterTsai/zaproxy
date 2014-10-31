/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package org.zaproxy.zap.extension.spider;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.table.AbstractTableModel;

import org.parosproxy.paros.Constant;

/**
 * The Class HttpSessionsTableModel that is used as a TableModel for the Http Sessions Panel.
 */
public class SpiderPanelTableModel extends AbstractTableModel {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -6380136823410869457L;

	/** The column names. */
	private static final String[] COLUMN_NAMES = { Constant.messages.getString("spider.table.header.inScope"),
			Constant.messages.getString("spider.table.header.method"),
			Constant.messages.getString("spider.table.header.uri"),
			Constant.messages.getString("spider.table.header.flags") };

	/** The Constant defining the COLUMN COUNT. */
	private static final int COLUMN_COUNT = COLUMN_NAMES.length;

	/** The Spider scan results. */
	private List<SpiderScanResult> scanResults;

	/** The Constant inScopeIcon. */
	private static final ImageIcon skippedIcon;

	/** The Constant outOfScopeIcon. */
	private static final ImageIcon notSkippedIcon;

	static {
		skippedIcon = new ImageIcon(SpiderPanelTableModel.class.getResource("/resource/icon/16/149.png"));
		notSkippedIcon = new ImageIcon(SpiderPanelTableModel.class.getResource("/resource/icon/16/152.png"));
	}

	/**
	 * Instantiates a new spider panel table model.
	 */
	public SpiderPanelTableModel() {
		super();

		scanResults = new ArrayList<>();
	}

	@Override
	public String getColumnName(int column) {
		return COLUMN_NAMES[column];
	}

	@Override
	public int getColumnCount() {
		return COLUMN_COUNT;
	}

	@Override
	public int getRowCount() {
		return scanResults.size();
	}

	@Override
	public Object getValueAt(int row, int col) {
		// Get the ScanResult and the required field
		SpiderScanResult result = scanResults.get(row);
		switch (col) {
		case 0:
			if (result.skipped) {
				return skippedIcon;
			} else {
				return notSkippedIcon;
			}
		case 1:
			return result.method;
		case 2:
			return result.uri;
		case 3:
			// TODO: Internationalize flags
			return result.flags;
		default:
			return null;
		}
	}

	/**
	 * Removes all the elements. Method is synchronized internally.
	 */
	public void removeAllElements() {
		synchronized (scanResults) {
			scanResults.clear();
			fireTableDataChanged();
		}
	}

	/**
	 * Adds a new spider scan result. Method is synchronized internally.
	 * 
	 * @param uri the uri
	 * @param method the method
	 * @param flags the flags
	 * @param inScope the in scope
	 */
	public void addScanResult(String uri, String method, String flags, boolean inScope) {
		SpiderScanResult result = new SpiderScanResult(uri, method, flags, inScope);
		synchronized (scanResults) {
			scanResults.add(result);
			try {
				fireTableRowsInserted(scanResults.size() - 1, scanResults.size() - 1);
			} catch (IndexOutOfBoundsException e) {
				// Happens occasionally but seems benign
			}
		}
	}

	/**
	 * Removes the scan result for a particular uri and method. Method is synchronized internally.
	 * 
	 * @param uri the uri
	 * @param method the method
	 */
	public void removesScanResult(String uri, String method) {
		SpiderScanResult toRemove = new SpiderScanResult(uri, method);
		synchronized (scanResults) {
			int index = scanResults.indexOf(toRemove);
			if (index >= 0) {
				scanResults.remove(index);
				fireTableRowsDeleted(index, index);
			}
		}
	}

	/**
	 * Returns the type of column for given column index.
	 * 
	 * @param columnIndex the column index
	 * @return the column class
	 */
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case 0:
			return ImageIcon.class;
		case 1:
			return String.class;
		case 2:
			return String.class;
		case 3:
			return String.class;
		}
		return null;
	}

	/**
	 * The Class SpiderScanResult that stores an entry in the table (a result for the spidering
	 * process).
	 */
	private static class SpiderScanResult {

		/** The uri. */
		protected String uri;

		/** The method. */
		protected String method;

		/** The flags. */
		protected String flags;

		/** The in scope. */
		protected boolean skipped;

		/**
		 * Instantiates a new spider scan result.
		 * 
		 * @param uri the uri
		 * @param method the method
		 */
		protected SpiderScanResult(String uri, String method) {
			super();
			this.uri = uri;
			this.method = method;
		}

		/**
		 * Instantiates a new spider scan result.
		 * 
		 * @param uri the uri
		 * @param method the method
		 * @param flags the flags
		 * @param skipped the in scope
		 */
		protected SpiderScanResult(String uri, String method, String flags, boolean skipped) {
			super();
			this.uri = uri;
			this.method = method;
			this.flags = flags;
			this.skipped = skipped;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((method == null) ? 0 : method.hashCode());
			result = prime * result + ((uri == null) ? 0 : uri.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			// Removed some irrelevant checks, to speed up the method.
			SpiderScanResult other = (SpiderScanResult) obj;
			if (method == null) {
				if (other.method != null)
					return false;
			} else if (!method.equals(other.method))
				return false;
			if (uri == null) {
				if (other.uri != null)
					return false;
			} else if (!uri.equals(other.uri))
				return false;
			return true;
		}
	}
}
