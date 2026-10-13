package fixtures;
/** Compiled fixture: no library is loaded and these declarations are never invoked. */
public class NativeMethods {
    public native int nativeCall(String input);
    public static native void nativeStatic();
    public int ordinary() { return 1; }
}
