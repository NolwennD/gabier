module eu.gabier.daemon {
    requires static org.jspecify;
    requires eu.gabier.core;
    requires eu.gabier.protocol;
    requires eu.gabier.shared;
    requires org.eclipse.lsp4j;
    requires com.google.gson;

    // LSP4J finds the methods of the jdtls client (the language/status notification) and calls them by reflection, and
    // Gson fills the records of its messages: both need the package open, which a class path run never asks for.
    opens eu.gabier.daemon to
            com.google.gson,
            org.eclipse.lsp4j.jsonrpc;
}
