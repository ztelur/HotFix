package dodola.hotfixlib.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * shanbay
 * Created by  tian.zhang@shanbay.com
 * date : 15-11-30.
 * time : 下午5:01
 */
@Retention(RetentionPolicy.CLASS)
public @interface Patch {
	boolean included() default false;
}
