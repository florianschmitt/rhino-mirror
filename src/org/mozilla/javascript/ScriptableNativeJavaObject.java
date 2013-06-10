package org.mozilla.javascript;

public class ScriptableNativeJavaObject extends ScriptableObject {
    NativeJavaObject prototype;

    public ScriptableNativeJavaObject(Scriptable scope, Object javaObject, Class staticType) {
        super(scope, new NativeJavaObject(scope, javaObject, staticType));
        this.prototype = (NativeJavaObject) this.getPrototype();
    }

    public String getClassName() {
        return prototype.unwrap().getClass().getName();
    }

    public static class ScriptableNativeContextFactory extends ContextFactory {
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setWrapFactory(new ScriptableNativeWrapFactory());
            return cx;
        }
    }
    
    public static class ScriptableNativeWrapFactory extends WrapFactory {
        ScriptableNativeWrapFactory() {
            this.setJavaPrimitiveWrap(false);
        }
        
        public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {
            return new ScriptableNativeJavaObject(scope, javaObject, staticType);
        }
    }
    
    public NativeJavaObject getJavaPrototype() {
    	return prototype;
    }
}