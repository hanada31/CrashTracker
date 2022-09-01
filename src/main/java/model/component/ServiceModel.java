package main.java.model.component;

import main.java.model.analyzeModel.AppModel;
import main.java.utils.ConstantUtils;

public class ServiceModel extends ComponentModel  {
	private static final long serialVersionUID = 3L;

	public ServiceModel(AppModel appModel) {
		super(appModel);
		type = "s";
	}

	@Override
	public String getComponentType() {
		return ConstantUtils.SERVICE;
	}
}
