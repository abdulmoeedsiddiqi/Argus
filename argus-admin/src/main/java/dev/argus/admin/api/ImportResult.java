package dev.argus.admin.api;

import java.util.List;

/**
 * What an import did.
 *
 * @param created     entities that did not exist before
 * @param updated     entities that existed and were overwritten with the document's values
 * @param issuedKeys  keys issued by this import, each shown in full this one time only
 */
public record ImportResult(int created, int updated, List<IssuedKeyDto> issuedKeys) { }
