module fr.craft.gabier.daemon {
    requires static org.jspecify;
    requires fr.craft.gabier.protocol;
    requires static fr.craft.gabier.shared;
    requires org.eclipse.lsp4j;
    requires com.google.gson;

    // LSP4J finds the methods of the jdtls client (the language/status notification) and calls them by reflection, and
    // Gson fills the records of its messages: both need the package open, which a class path run never asks for.
    opens fr.craft.gabier.daemon to
            com.google.gson,
            org.eclipse.lsp4j.jsonrpc;
}
