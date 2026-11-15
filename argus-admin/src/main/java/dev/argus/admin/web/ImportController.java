package dev.argus.admin.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.argus.admin.api.ImportDocument;
import dev.argus.admin.api.ImportResult;
import dev.argus.admin.security.AdminAuthenticationFilter;
import dev.argus.admin.service.ImportService;
import dev.argus.core.exception.InvalidConfigurationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seed endpoint: one YAML document with plans, upstream instances, routes and consumers.
 * Used by scripts/seed.sh so that the demo is set up through the Admin API with one command,
 * never by writing to the database.
 */
@RestController
public class ImportController {

    private final ImportService imports;
    private final YAMLMapper yaml = new YAMLMapper();

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping(
            value = "/admin/import",
            consumes = {"application/yaml", "application/x-yaml", "text/yaml"})
    public ImportResult importDocument(
            @RequestBody String document,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        return imports.apply(parse(document), actor);
    }

    private ImportDocument parse(String document) {
        try {
            ImportDocument parsed = yaml.readValue(document, ImportDocument.class);
            if (parsed == null) {
                throw new InvalidConfigurationException("the document is empty");
            }
            return parsed;
        } catch (JsonProcessingException e) {
            // getOriginalMessage has the reason and the line, without Java class names.
            throw new InvalidConfigurationException(
                    "the document is not valid YAML for an import: " + e.getOriginalMessage());
        }
    }
}
