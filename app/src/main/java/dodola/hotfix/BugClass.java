/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package dodola.hotfix;

import dodola.hotfixlib.annotation.Patch;

/**
 * Created by sunpengfei on 15/11/3.
 */
@Patch(included = true)
public class BugClass {

    public String bug() {
        return "bug class";
    }
}
