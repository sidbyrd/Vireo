package org.tdl.vireo.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a simple mock settings repo class that may be useful for testing.
 * Feel free to extend this to add in extra parameters that you feel
 * appropriate.
 *
 * Currently, it only has mock implementations for Configuration related methods.
 *
 * The basic concept is all properties are public so you can create the mock
 * object and then set whatever relevant properties are needed for your
 * particular test.
 */
public class MockSettingsRepository implements SettingsRepository {

    public Map<String, MockConfiguration> configs = new HashMap<String, MockConfiguration>(4);

    public MockSettingsRepository() {
    }

    @Override
    public Degree createDegree(String name, DegreeLevel level) {
        return null;
    }

    @Override
    public Degree findDegree(Long id) {
        return null;
    }

    @Override
    public Degree findDegreeByName(String name) {
        return null;
    }

    @Override
    public List<Degree> findAllDegrees() {
        return null;
    }

    @Override
    public Major createMajor(String name) {
        return null;
    }

    @Override
    public Major findMajor(Long id) {
        return null;
    }

    @Override
    public List<Major> findAllMajors() {
        return null;
    }

    @Override
    public College createCollege(String name) {
        return null;
    }

    @Override
    public College findCollege(Long id) {
        return null;
    }

    @Override
    public List<College> findAllColleges() {
        return null;
    }

    @Override
    public Program createProgram(String name) {
        return null;
    }

    @Override
    public Program findProgram(Long id) {
        return null;
    }

    @Override
    public List<Program> findAllPrograms() {
        return null;
    }

    @Override
    public Department createDepartment(String name) {
        return null;
    }

    @Override
    public Department findDepartment(Long id) {
        return null;
    }

    @Override
    public List<Department> findAllDepartments() {
        return null;
    }

    @Override
    public DocumentType createDocumentType(String name, DegreeLevel level) {
        return null;
    }

    @Override
    public DocumentType findDocumentType(Long id) {
        return null;
    }

    @Override
    public List<DocumentType> findAllDocumentTypes(DegreeLevel level) {
        return null;
    }

    @Override
    public List<DocumentType> findAllDocumentTypes() {
        return null;
    }

    @Override
    public Language createLanguage(String name) {
        return null;
    }

    @Override
    public Language findLanguage(Long id) {
        return null;
    }

    @Override
    public Language findLanguageByName(String name) {
        return null;
    }

    @Override
    public List<Language> findAllLanguages() {
        return null;
    }

    @Override
    public EmbargoType createEmbargoType(String name, String description, Integer duration, boolean active) {
        return null;
    }

    @Override
    public EmbargoType findEmbargoType(Long id) {
        return null;
    }

    @Override
    public List<EmbargoType> findAllEmbargoTypes() {
        return null;
    }

    @Override
    public List<EmbargoType> findAllActiveEmbargoTypes() {
        return null;
    }

    @Override
    public GraduationMonth createGraduationMonth(int month) {
        return null;
    }

    @Override
    public GraduationMonth findGraduationMonth(Long id) {
        return null;
    }

    @Override
    public List<GraduationMonth> findAllGraduationMonths() {
        return null;
    }

    @Override
    public CommitteeMemberRoleType createCommitteeMemberRoleType(String name, DegreeLevel level) {
        return null;
    }

    @Override
    public CommitteeMemberRoleType findCommitteeMemberRoleType(Long id) {
        return null;
    }

    @Override
    public List<CommitteeMemberRoleType> findAllCommitteeMemberRoleTypes(DegreeLevel level) {
        return null;
    }

    @Override
    public List<CommitteeMemberRoleType> findAllCommitteeMemberRoleTypes() {
        return null;
    }

    @Override
    public EmailTemplate createEmailTemplate(String name, String subject, String message) {
        return null;
    }

    @Override
    public EmailTemplate findEmailTemplate(Long id) {
        return null;
    }

    @Override
    public EmailTemplate findEmailTemplateByName(String name) {
        return null;
    }

    @Override
    public List<EmailTemplate> findAllEmailTemplates() {
        return null;
    }

    @Override
    public CustomActionDefinition createCustomActionDefinition(String label) {
        return null;
    }

    @Override
    public CustomActionDefinition findCustomActionDefinition(Long id) {
        return null;
    }

    @Override
    public List<CustomActionDefinition> findAllCustomActionDefinition() {
        return null;
    }

    @Override
    public Configuration createConfiguration(String name, String value) {
        // because whomever calls this may want to modify the key of the
        // created config, and that's not 100% trivial, and I don't need it.
        throw new RuntimeException("not implemented");
    }

    @Override
    public Configuration findConfiguration(Long id) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public Configuration findConfigurationByName(String name) {
        MockConfiguration found = configs.get(name);
        if (found==null) {
            return null;
        }
        if (found.deleted) {
            configs.remove(found);
            return null;
        }
        return found;
    }

    @Override
    public String getConfigValue(String name, String defaultValue) {
        MockConfiguration found = (MockConfiguration)findConfigurationByName(name);
        if (found == null || found.getValue() == null || found.getValue().trim().length() == 0) {
            return defaultValue;
        }
        return found.getValue();
    }

    @Override
    public String getConfigValue(String name) {
        return getConfigValue(name, Configuration.DEFAULTS.get(name));
    }

    @Override
    public boolean getConfigBoolean(String name) {
        return getConfigValue(name) != null;
    }

    @Override
    public int getConfigInt(String field) {
        try {
            return Integer.parseInt(getConfigValue(field));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public List<Configuration> findAllConfigurations() {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void saveConfiguration(String field, String value) {
        configs.put(field, new MockConfiguration(field, value));
    }

    @Override
    public DepositLocation createDepositLocation(String name) {
        return null;
    }

    @Override
    public DepositLocation findDepositLocation(Long id) {
        return null;
    }

    @Override
    public DepositLocation findDepositLocationByName(String name) {
        return null;
    }

    @Override
    public List<DepositLocation> findAllDepositLocations() {
        return null;
    }
}
