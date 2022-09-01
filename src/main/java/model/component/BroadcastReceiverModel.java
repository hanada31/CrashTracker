package main.java.model.component;

import main.java.model.analyzeModel.AppModel;
import main.java.utils.ConstantUtils;

public class BroadcastReceiverModel extends ComponentModel {
	private static final long serialVersionUID = 3L;

	public BroadcastReceiverModel(AppModel appModel) {
		super(appModel);
		type = "r";
	}

	@Override
	public String getComponentType() {
		return ConstantUtils.RECEIVER;
	}

}
