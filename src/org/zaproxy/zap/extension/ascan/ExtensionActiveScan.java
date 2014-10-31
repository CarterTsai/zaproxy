/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Copyright 2010 psiinon@gmail.com
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
package org.zaproxy.zap.extension.ascan;

import java.awt.Dimension;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.control.Control.Mode;
import org.parosproxy.paros.core.scanner.HostProcess;
import org.parosproxy.paros.core.scanner.ScannerParam;
import org.parosproxy.paros.db.TableHistory;
import org.parosproxy.paros.extension.CommandLineArgument;
import org.parosproxy.paros.extension.CommandLineListener;
import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;
import org.parosproxy.paros.extension.SessionChangedListener;
import org.parosproxy.paros.extension.history.ProxyListenerLog;
import org.parosproxy.paros.model.HistoryReference;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.Session;
import org.parosproxy.paros.model.SiteNode;
import org.parosproxy.paros.view.AbstractParamPanel;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.ZAP;
import org.zaproxy.zap.extension.alert.ExtensionAlert;
import org.zaproxy.zap.extension.api.API;
import org.zaproxy.zap.extension.help.ExtensionHelp;
import org.zaproxy.zap.extension.pscan.ExtensionPassiveScan;
import org.zaproxy.zap.extension.pscan.PolicyPassiveScanPanel;
import org.zaproxy.zap.extension.script.ExtensionScript;
import org.zaproxy.zap.extension.script.ScriptType;
import org.zaproxy.zap.extension.script.ScriptWrapper;
import org.zaproxy.zap.model.GenericScanner2;
import org.zaproxy.zap.model.ScanController;
import org.zaproxy.zap.model.Target;
import org.zaproxy.zap.users.User;
import org.zaproxy.zap.view.ZapMenuItem;

public class ExtensionActiveScan extends ExtensionAdaptor implements
        SessionChangedListener, CommandLineListener, ScanController {

    private static final Logger logger = Logger.getLogger(ExtensionActiveScan.class);
    private static final int ARG_SCAN_IDX = 0;
    
    public static final String NAME = "ExtensionActiveScan";
    
    private static final ImageIcon SCRIPT_ICON_ACTIVE
            = new ImageIcon(ZAP.class.getResource("/resource/icon/16/script-ascan.png"));
    
    private static final ImageIcon SCRIPT_ICON_VARIANT
            = new ImageIcon(ZAP.class.getResource("/resource/icon/16/script-variant.png"));

    public static final String SCRIPT_TYPE_ACTIVE = "active";
    public static final String SCRIPT_TYPE_VARIANT = "variant";

    //Could be after the last one that saves the HttpMessage, as this ProxyListener doesn't change the HttpMessage.
    public static final int PROXY_LISTENER_ORDER = ProxyListenerLog.PROXY_LISTENER_ORDER + 1;
    private static final List<Class<?>> DEPENDENCIES;
    
    private AttackModeScanner attackModeScanner;
    
    private ActiveScanController ascanController = null;

    static {
        List<Class<?>> dep = new ArrayList<>(1);
        dep.add(ExtensionAlert.class);

        DEPENDENCIES = Collections.unmodifiableList(dep);
    }

    private ZapMenuItem menuItemPolicy = null;
    private ZapMenuItem menuItemCustomScan = null;
    private OptionsScannerPanel optionsScannerPanel = null;
    private OptionsVariantPanel optionsVariantPanel = null;
    private ActiveScanPanel activeScanPanel = null;
    private ScannerParam scannerParam = null;
    private final CommandLineArgument[] arguments = new CommandLineArgument[1];
    private final List<AbstractParamPanel> policyPanels = new ArrayList<>();
	private JButton policyButton = null;
	private CustomScanDialog customScanDialog = null;
	private List<ScriptWrapper> includedSequenceScripts = null;
    
	private ActiveScanAPI activeScanApi;

    /**
     *
     */
    public ExtensionActiveScan() {
        super();
        initialize();
    }

    /**
     * @param name
     */
    public ExtensionActiveScan(String name) {
        super(name);
    }

    /**
     * This method initializes this
     */
    private void initialize() {
        this.setName(NAME);
        this.setOrder(28);
        ascanController = new ActiveScanController(this);
        attackModeScanner = new AttackModeScanner(this);

    }

    @Override
    public void hook(ExtensionHook extensionHook) {
        super.hook(extensionHook);

		TableHistory.setHistoryTypeAsTemporary(HistoryReference.TYPE_SCANNER_TEMPORARY);

        if (getView() != null) {
            extensionHook.getHookMenu().addAnalyseMenuItem(getMenuItemPolicy());
            extensionHook.getHookMenu().addToolsMenuItem(getMenuItemCustomScan());

            extensionHook.getHookView().addStatusPanel(getActiveScanPanel());
            extensionHook.getHookView().addOptionPanel(getOptionsScannerPanel());
            extensionHook.getHookView().addOptionPanel(getOptionsVariantPanel());

	        View.getSingleton().addMainToolbarButton(this.getPolicyButton());
			View.getSingleton().getMainFrame().getMainFooterPanel().addFooterToolbarRightLabel(
					attackModeScanner.getScanStatus().getCountLabel());

            ExtensionHelp.enableHelpKey(getActiveScanPanel(), "ui.tabs.ascan");
        }
        
        extensionHook.addSessionListener(this);

        extensionHook.addOptionsParamSet(getScannerParam());
        // TODO this isnt currently implemented
        //extensionHook.addCommandLine(getCommandLineArguments());

        ExtensionScript extScript = (ExtensionScript) Control.getSingleton().getExtensionLoader().getExtension(ExtensionScript.NAME);
        if (extScript != null) {
            extScript.registerScriptType(new ScriptType(SCRIPT_TYPE_ACTIVE, "ascan.scripts.type.active", SCRIPT_ICON_ACTIVE, true));
            extScript.registerScriptType(new ScriptType(SCRIPT_TYPE_VARIANT, "variant.scripts.type.variant", SCRIPT_ICON_VARIANT, true));
        }

        this.ascanController.setExtAlert((ExtensionAlert) Control.getSingleton().getExtensionLoader().getExtension(ExtensionAlert.NAME));
        this.activeScanApi = new ActiveScanAPI(this);
        this.activeScanApi.addApiOptions(getScannerParam());
        API.getInstance().registerApiImplementor(activeScanApi);
    }

    private ActiveScanPanel getActiveScanPanel() {
        if (activeScanPanel == null) {
            activeScanPanel = new ActiveScanPanel(this);
        }        
        return activeScanPanel;
    }

    public void startScanAllInScope() {
        SiteNode snroot = (SiteNode) Model.getSingleton().getSession().getSiteTree().getRoot();
    	this.startScan(new Target(snroot, null, true, true));
    }

    /**
     * Start the scanning process beginning to a specific node 
     * @param startNode the start node where the scanning should begin to work
     */
    public int startScan(SiteNode startNode) {
    	return this.startScan(new Target(startNode, true));
    }

    public int startScanNode(SiteNode startNode) {
    	return this.startScan(new Target(startNode, false));
    }

    public int startScan(Target target) {
    	return this.startScan(target, null, null);
    }

	public int startScan(Target target, User user, Object[] contextSpecificObjects) {
    	return this.startScan(target.getDisplayName(), target, user, contextSpecificObjects);
	}

    @Override
	public int startScan(String name, Target target, User user, Object[] contextSpecificObjects) {
    	if (name == null) {
    		name = target.getDisplayName();
    	}
		
		switch (Control.getSingleton().getMode()) {
		case safe:
			throw new InvalidParameterException("Scans are not allowed in Safe mode");
		case protect:
			if (target.getStartNode() != null && ! target.getStartNode().isIncludedInScope()) {
				throw new InvalidParameterException("Scans are not allowed on nodes not in scope Protected mode " +
						target.getStartNode().getHierarchicNodeName());
			}
			// No problem
			break;
		case standard:
			// No problem
			break;
		case attack:
			// No problem
			break;
		}
		
		int id = this.ascanController.startScan(name, target, user, contextSpecificObjects);
    	if (View.isInitialised()) {
    		GenericScanner2 scanner = this.ascanController.getScan(id);
    		((ActiveScan)scanner).addScannerListener(getActiveScanPanel());	// So the UI get updated
			this.getActiveScanPanel().scannerStarted(scanner);
    		this.getActiveScanPanel().switchView(scanner);
    		this.getActiveScanPanel().setTabFocus();
    	}
    	return id;
	}

    public void scannerComplete() {
    }

	private JButton getPolicyButton() {
		if (policyButton == null) {
			policyButton = new JButton();
			policyButton.setIcon(new ImageIcon(ActiveScanPanel.class.getResource("/resource/icon/fugue/equalizer.png")));
			policyButton.setToolTipText(Constant.messages.getString("menu.analyse.scanPolicy"));

			policyButton.addActionListener(new java.awt.event.ActionListener() { 
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
                    showPolicyDialog();
				}
			});
		}
		return policyButton;
	}

    /**
     * This method initializes menuItemPolicy
     *
     * @return javax.swing.JMenuItem
     */
    private ZapMenuItem getMenuItemPolicy() {
        if (menuItemPolicy == null) {
            menuItemPolicy = new ZapMenuItem("menu.analyse.scanPolicy",
                    KeyStroke.getKeyStroke(KeyEvent.VK_P, Event.CTRL_MASK, false));

            menuItemPolicy.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    showPolicyDialog();
                }
            });

        }
        
        return menuItemPolicy;
    }

    protected void showPolicyDialog() {
        PolicyDialog dialog = new PolicyDialog(getView().getMainFrame());
        dialog.initParam(getModel().getOptionsParam());
        for (AbstractParamPanel panel : policyPanels) {
            dialog.addPolicyPanel(panel);
        }

        // TODO This could be done in a cleaner way...
        ExtensionPassiveScan pscan = (ExtensionPassiveScan) Control.getSingleton().getExtensionLoader().getExtension(ExtensionPassiveScan.NAME);
        if (pscan != null) {
            PolicyPassiveScanPanel ppanel = pscan.getPolicyPanel();
            ppanel.setAllCategoryPanel(dialog.getPolicyAllCategoryPanel());
            dialog.addPolicyPanel(ppanel);
        }

        int result = dialog.showDialog(true);
        if (result == JOptionPane.OK_OPTION) {
            try {
                getModel().getOptionsParam().getConfig().save();

            } catch (ConfigurationException ce) {
                logger.error(ce.getMessage(), ce);
                getView().showWarningDialog(Constant.messages.getString("scanner.save.warning"));
            }
        }
    }

    private ZapMenuItem getMenuItemCustomScan() {
        if (menuItemCustomScan == null) {
            menuItemCustomScan = new ZapMenuItem("menu.tools.ascanadv",
                    KeyStroke.getKeyStroke(KeyEvent.VK_A, Event.CTRL_MASK | Event.ALT_MASK, false));

            menuItemCustomScan.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                	showCustomScanDialog(null);
                }
            });

        }
        
        return menuItemCustomScan;
    }

    public void hostProgress(String hostAndPort, String msg, int percentage) {
    }

    public void hostComplete(String hostAndPort) {
    }

    public void hostNewScan(String hostAndPort, HostProcess hostThread) {
    }

    @Override
    public void sessionChanged(final Session session) {
        if (EventQueue.isDispatchThread()) {
            sessionChangedEventHandler(session);

        } else {
            try {
                EventQueue.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        sessionChangedEventHandler(session);
                    }
                });
                
            } catch (InterruptedException | InvocationTargetException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private void sessionChangedEventHandler(Session session) {
        // Clear all scans
        this.getActiveScanPanel().reset();
        if (session == null) {
            // Closedown
            return;
        }
    }

    /**
     * This method initializes optionsScannerPanel
     *
     * @return org.parosproxy.paros.extension.scanner.OptionsScannerPanel
     */
    private OptionsScannerPanel getOptionsScannerPanel() {
        if (optionsScannerPanel == null) {
            optionsScannerPanel = new OptionsScannerPanel();
        }
        return optionsScannerPanel;
    }

    /**
     * This method initializes optionsVariantPanel
     *
     * @return org.zaproxy.zap.extension.ascan.OptionsVariantPanel
     */
    private OptionsVariantPanel getOptionsVariantPanel() {
        if (optionsVariantPanel == null) {
            optionsVariantPanel = new OptionsVariantPanel();
        }
        return optionsVariantPanel;
    }

    /**
     * This method initializes scannerParam
     *
     * @return org.parosproxy.paros.core.scanner.ScannerParam
     */
    protected ScannerParam getScannerParam() {
        if (scannerParam == null) {
            scannerParam = new ScannerParam();
        }
        return scannerParam;
    }

    // TODO
    @Override
    public void execute(CommandLineArgument[] args) {
        /*
         if (arguments[ARG_SCAN_IDX].isEnabled()) {
         System.out.println("Scanner started...");
         startScan();
         } else {
         return;
         }

         while (!getScanner().isStop()) {
         try {
         Thread.sleep(1000);
         } catch (InterruptedException e) {
         }
         }
         System.out.println("Scanner completed.");
         */
    }

    @SuppressWarnings("unused")
    private CommandLineArgument[] getCommandLineArguments() {
        arguments[ARG_SCAN_IDX] = new CommandLineArgument("-scan", 0, null, "", "-scan : Run vulnerability scan depending on previously saved policy.");
        return arguments;
    }

	public void setExcludeList(List<String> urls) {
		for (GenericScanner2 scanner : ascanController.getActiveScans()) {
			((ActiveScan)scanner).setExcludeList(urls);
		}
	}


    public void addPolicyPanel(AbstractParamPanel panel) {
        this.policyPanels.add(panel);
    }

    @Override
    public List<Class<?>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void sessionAboutToChange(final Session session) {
        if (activeScanApi != null) {
            activeScanApi.reset();
        }

        // Shut all of the scans down
        this.getActiveScanPanel().reset();
    }

    @Override
    public String getAuthor() {
        return Constant.ZAP_TEAM;
    }

    @Override
    public String getDescription() {
        return Constant.messages.getString("ascan.desc");
    }

    @Override
    public URL getURL() {
        try {
            return new URL(Constant.ZAP_HOMEPAGE);
            
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
    public void sessionScopeChanged(Session session) {
        this.getActiveScanPanel().sessionScopeChanged(session);
        this.attackModeScanner.sessionScopeChanged(session);
    }

    @Override
    public void sessionModeChanged(Mode mode) {
    	if (Mode.safe.equals(mode)) {
    		this.ascanController.stopAllScans();
    	}
    	getMenuItemCustomScan().setEnabled( ! Mode.safe.equals(mode));
        this.getActiveScanPanel().sessionModeChanged(mode);
        this.attackModeScanner.sessionModeChanged(mode);
        
    }

    @Override
    public void destroy() {
        // Shut all of the scans down
        this.getActiveScanPanel().reset();

        if (activeScanApi != null) {
            activeScanApi.reset();
        }
    }

	public void showCustomScanDialog(SiteNode node) {
		if (customScanDialog == null) {
			customScanDialog = new CustomScanDialog(this, View.getSingleton().getMainFrame(), new Dimension(700, 500));
		}
		if (customScanDialog.isVisible()) {
			// Its behind you! Actually not needed no the window is alwaysOnTop, but keeping in case we change that ;)
			customScanDialog.toFront();
			return;
		}
		if (node != null) {
			customScanDialog.init(new Target(node));
		} else {
			// Keep the previous target
			customScanDialog.init(null);
		}
		customScanDialog.setVisible(true);
	}
    
    @Override
    public boolean handleFile(File file) {
        // Cant handle any files
        return false;
    }

    @Override
    public List<String> getHandledExtensions() {
        // Cant handle any extensions
        return null;
    }


	@Override
	public List<GenericScanner2> getAllScans() {
		return ascanController.getAllScans();
	}

	@Override
	public List<GenericScanner2> getActiveScans() {
		return ascanController.getActiveScans();
	}

	@Override
	public GenericScanner2 getScan(int id) {
		return ascanController.getScan(id);
	}

	@Override
	public void stopScan(int id) {
		ascanController.stopScan(id);
		// Dont need to update the UI - this will happen automatically via the events
	}

	@Override
	public void pauseScan(int id) {
		ascanController.pauseScan(id);
		if (View.isInitialised()) {
			// Update the UI in case this was initiated from the API
			this.getActiveScanPanel().updateScannerUI();
		}
	}

	@Override
	public void resumeScan(int id) {
		ascanController.resumeScan(id);
		if (View.isInitialised()) {
			// Update the UI in case this was initiated from the API
			this.getActiveScanPanel().updateScannerUI();
		}
	}

	@Override
	public void stopAllScans() {
		ascanController.stopAllScans();		
		// Dont need to update the UI - this will happen automatically via the events
	}

	@Override
	public void pauseAllScans() {
		ascanController.pauseAllScans();		
		if (View.isInitialised()) {
			// Update the UI in case this was initiated from the API
			this.getActiveScanPanel().updateScannerUI();
		}
	}

	@Override
	public void resumeAllScans() {
		ascanController.removeAllScans();		
		if (View.isInitialised()) {
			// Update the UI in case this was initiated from the API
			this.getActiveScanPanel().updateScannerUI();
		}
	}

	@Override
	public GenericScanner2 removeScan(int id) {
		return ascanController.removeScan(id);
	}

	@Override
	public int removeAllScans() {
		return ascanController.removeAllScans();
	}

	@Override
	public int removeFinishedScans() {
		return ascanController.removeFinishedScans();
	}

	@Override
	public GenericScanner2 getLastScan() {
		return ascanController.getLastScan();
	}

	public void setIncludedSequenceScripts(List<ScriptWrapper> selectedIncludeScripts) {
		this.includedSequenceScripts = selectedIncludeScripts;
	}
	
	public List<ScriptWrapper> getIncludedSequenceScripts() {
		return this.includedSequenceScripts;
	}
}
