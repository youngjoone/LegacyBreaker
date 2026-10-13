// Open this directory as a Java source root to try the plugin (Java 8).
class Example {
    final Store store = new Store();

    void entry() { save(2); }
    void secondCaller() { save(3); }
    void overloadCaller() { save("text"); }

    void save(int count) {
        validate();
        store.write();
        if (count > 0) save(count - 1); // recursion reuses the same graph node
        System.currentTimeMillis();  // library node: source/decompiler navigation only
    }

    void save(String value) { validate(); }
    void validate() { }
    void deferred() {
        Runnable lambda = () -> save(9); // excluded: execution is deferred
        Runnable anonymous = new Runnable() {
            { validate(); } // excluded: class initializer
            public void run() { save(8); } // caller belongs to run(), not deferred()
        };
    }
    void nested() {
        class Local {
            void work() { validate(); } // belongs to Local.work()
        }
    }
}

class Store {
    void write() { audit(); }
    void audit() { write(); } // cycle shown without endless expansion
}
