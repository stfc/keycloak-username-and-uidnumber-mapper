package uk.ac.stfc.auth.mapper;

import com.google.auto.service.AutoService;
import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityProviderMapper;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@AutoService(IdentityProviderMapper.class)
public class UsernameAndUIDNumberMapper extends AbstractIdentityProviderMapper {

    public static final String PROVIDER_ID = "username-and-uidnumber-mapper";
    public static final String[] COMPATIBLE_PROVIDERS = {ANY_PROVIDER};
    protected static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final Set<IdentityProviderSyncMode> IDENTITY_PROVIDER_SYNC_MODES = new HashSet<>(Arrays.asList(IdentityProviderSyncMode.values()));
    private static final String PATH = "path";
    private static final String PREFIX = "prefix";
    private static final String UID_NUMBER_ATTRIBUTE = "uidNumber";
    private static final String GID_NUMBER_ATTRIBUTE = "gidNumber";
    private static final String HOME_DIRECTORY_ATTRIBUTE = "homeDirectory";
    private static final String SHELL_ATTRIBUTE = "loginShell";
    private static final String SLURM_ACCOUNT = "slurmAccount";

    /*
     * The properties that we need the user to configure to make this mapper work properly
     * such as the file path for the next uidNumber and username/SLURM bits
     */
    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(PATH);
        property.setLabel("uidNumber File Path");
        property.setHelpText("Path to file on disk that contains the next uidNumber");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(PREFIX);
        property.setLabel("Username Prefix");
        property.setHelpText("Username Prefix");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(GID_NUMBER_ATTRIBUTE);
        property.setLabel("gidNumber");
        property.setHelpText("Group ID Number");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(SLURM_ACCOUNT);
        property.setLabel("SLURM account");
        property.setHelpText("SLURM account");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(SHELL_ATTRIBUTE);
        property.setLabel("Login Shell");
        property.setHelpText("Login Shell");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);
    }

    /**
     * Get whether this mapper supports a particular sync mode
     * @param syncMode the sync mode we wish to use
     * @return true or false whether the sync mode is supported
     */
    @Override
    public boolean supportsSyncMode(IdentityProviderSyncMode syncMode) {
        return IDENTITY_PROVIDER_SYNC_MODES.contains(syncMode);
    }

    /**
     * Get compatible providers for this mapper (all of them)
     * @return compatible providers
     */
    @Override
    public String[] getCompatibleProviders() {
        return COMPATIBLE_PROVIDERS;
    }

    /**
     * Get the category of this mapper
     * @return Attribute Importer
     */
    @Override
    public String getDisplayCategory() {
        return "Attribute Importer";
    }

    /**
     * Get the type of this mapper
     * @return the type of this mapper
     */
    @Override
    public String getDisplayType() {
        return "Username and uidNumber Generator";
    }

    /**
     * Help text for the mapper
     * @return string on how to use mapper
     */
    @Override
    public String getHelpText() {
        return "When a user is imported from a provider, generate them an appropriate username and uidNumber";
    }

    /**
     * Get the configuration properties that this mapper needs to be set
     * @return list of configuration properties
     */
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    /**
     * Get a unique identifier for this provider
     * @return the provider ID
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * Import a new user into the realm, on the first time they login when their account does not already exist only.
     * Set their username based on the prefix and next uidNumber
     * Set their home directory, gidNumber and shell, and add them to the right SLURM account
     * @param session current Keycloak session
     * @param realm the realm we are logging into
     * @param user the user we are modifying
     * @param mapperModel the current mapper
     * @param context the current broker context
     */
    @Override
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        String nextId = getNextIdAndIncrement(mapperModel);
        String username = getNextUsername(nextId, mapperModel);
        String homeDirectory = "/home/vol" + String.format("%02d", Integer.parseInt(nextId) % 10) + "/" + username;
        user.setUsername(username);
        user.setSingleAttribute(UID_NUMBER_ATTRIBUTE, nextId);
        user.setSingleAttribute(GID_NUMBER_ATTRIBUTE, mapperModel.getConfig().get(GID_NUMBER_ATTRIBUTE));
        user.setSingleAttribute(HOME_DIRECTORY_ATTRIBUTE, homeDirectory);
        user.setSingleAttribute(SHELL_ATTRIBUTE, mapperModel.getConfig().get(SHELL_ATTRIBUTE));
        if(!Objects.equals(mapperModel.getConfig().get(SLURM_ACCOUNT), "")) {
            addUserToSlurm(username, mapperModel);
        }
    }

    /**
     * Add a user to the correct SLURM account
     * @param username the username of the user
     * @param mapperModel the model of this mapper
     */
    private void addUserToSlurm(String username, IdentityProviderMapperModel mapperModel) {
        try {
            ProcessBuilder builder = new ProcessBuilder().command("/usr/bin/sacctmgr", "add", "user", username, "defaultaccount=" + mapperModel.getConfig().get(SLURM_ACCOUNT), "-i");
            builder.directory(new File("/")); // Set to / path for running this command
            Process p = builder.start();
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the next username we should use in the format prefixXXXX
     * @param nextId the next gidNumber
     * @param mapperModel the model of this mapper
     * @return a username in the format prefixXXXX where XXXX is the last 4 digits of the uidNumber
     */
    private String getNextUsername(String nextId, IdentityProviderMapperModel mapperModel) {
        return mapperModel.getConfig().get(PREFIX) + nextId.substring(nextId.length() - 4);
    }

    /**
     * Get the next uidNumber from the file on disk and increment it by one
     * @param mapperModel the model of this mapper
     * @return the next uidNumber to use
     */
    private String getNextIdAndIncrement(IdentityProviderMapperModel mapperModel) {
        String path = mapperModel.getConfig().get(PATH);
        Properties props = new Properties();
        try {
            InputStream is = new FileInputStream(path);
            props.load(is);
            is.close();
            String currentId = props.getProperty("nextId");
            Integer nextId = Integer.parseInt(currentId) + 1;
            props.setProperty("nextId", String.valueOf(nextId));
            FileOutputStream fos = new FileOutputStream(path);
            props.store(fos, "");
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props.getProperty("nextId");
    }
}
