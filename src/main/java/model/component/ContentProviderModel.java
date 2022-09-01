package main.java.model.component;

import main.java.model.analyzeModel.AppModel;
import main.java.utils.ConstantUtils;

public class ContentProviderModel extends ComponentModel {
	private static final long serialVersionUID = 3L;

	public ContentProviderModel(AppModel appModel) {
		super(appModel);
		type = "p";
	}

	@Override
	public String getComponentType() {
		return ConstantUtils.PROVIDER;
	}
}
