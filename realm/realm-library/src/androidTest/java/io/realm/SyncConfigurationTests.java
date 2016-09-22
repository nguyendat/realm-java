/*
 * Copyright 2016 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.realm.entities.StringOnly;
import io.realm.rule.RunInLooperThread;
import io.realm.rule.TestRealmConfigurationFactory;

import static io.realm.util.SyncTestUtils.createTestUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SyncConfigurationTests {
    @Rule
    public final TestRealmConfigurationFactory configFactory = new TestRealmConfigurationFactory();

    @Rule
    public final RunInLooperThread looperThread = new RunInLooperThread();

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getContext();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void user() {
//        new SyncConfiguration.Builder(context);
        // Check that user can be added
        // That the default local path is correct
    }

    @Test
    public void user_invalidUserThrows() {
        try {
            new SyncConfiguration.Builder(null, "realm://ros.realm.io/default");
        } catch (IllegalArgumentException ignore) {
        }

        User user = createTestUser(0); // Create user that has expired credentials
        try {
            new SyncConfiguration.Builder(user, "realm://ros.realm.io/default");
        } catch (IllegalArgumentException ignore) {
        }
    }

    @Test
    public void serverUrl_setsFolderAndFileName() {
        User user = createTestUser();
        String[][] validUrls = {
                // <URL>, <Folder>, <FileName>
                { "realm://objectserver.realm.io/~/default", "realm-object-server/" + user.getIdentity(), "default" },
                { "realm://objectserver.realm.io/~/sub/default", "realm-object-server/" + user.getIdentity() + "/sub", "default" }
        };

        for (String[] validUrl : validUrls) {
            String serverUrl  = validUrl[0];
            String expectedFolder = validUrl[1];
            String expectedFileName = validUrl[2];

            SyncConfiguration config = new SyncConfiguration.Builder(user, serverUrl).build();

            assertEquals(new File(context.getFilesDir(), expectedFolder), config.getRealmDirectory());
            assertEquals(expectedFileName, config.getRealmFileName());
        }
    }

    @Test
    public void serverUrl_invalidUrlThrows() {
        String[] invalidUrls = {
            null,
// TODO Should these two fail?
//            "objectserver.realm.io/~/default", // Missing protocol. TODO Should we just default to one?
//            "/~/default", // Missing server
            "realm://objectserver.realm.io/~/default.realm", // Ending with .realm
            "realm://objectserver.realm.io/~/default.realm.lock", // Ending with .realm.lock
            "realm://objectserver.realm.io/~/default.realm.management", // Ending with .realm.management
            "realm://objectserver.realm.io/<~>/default.realm", // Invalid chars <>
            "realm://objectserver.realm.io/~/default.realm/", // Ending with /
            "realm://objectserver.realm.io/~/Αθήνα", // Non-ascii
            "realm://objectserver.realm.io/~/foo/../bar", // .. is not allowed
            "realm://objectserver.realm.io/~/foo/./bar", // . is not allowed
            "http://objectserver.realm.io/~/default", // wrong scheme
        };

        for (String invalidUrl : invalidUrls) {
            try {
                new SyncConfiguration.Builder(createTestUser(), invalidUrl);
                fail(invalidUrl + " should have failed.");
            } catch (IllegalArgumentException ignore) {
            }
        }
    }

    private String makeServerUrl(int len) {
        StringBuilder builder = new StringBuilder("realm://objectserver.realm.io/~/");
        for (int i = 0; i < len; i++) {
            builder.append('A');
        }
        return builder.toString();
    }

    @Test
    public void serverUrl_length() {
        int[] lengths = {1, SyncConfiguration.MAX_FILE_NAME_LENGTH - 1,
                SyncConfiguration.MAX_FILE_NAME_LENGTH, SyncConfiguration.MAX_FILE_NAME_LENGTH + 1, 1000};

        for (int len : lengths) {
            SyncConfiguration config = new SyncConfiguration.Builder(createTestUser(), makeServerUrl(len)).build();
            assertTrue("Length: " + len, config.getRealmFileName().length() <= SyncConfiguration.MAX_FILE_NAME_LENGTH);
            assertTrue("Length: " + len, config.getPath().length() <= SyncConfiguration.MAX_FULL_PATH_LENGTH);
        }
    }

    @Test
    public void serverUrl_invalidChars() {
        SyncConfiguration.Builder builder = new SyncConfiguration.Builder(createTestUser(), "realm://objectserver.realm.io/~/?");
        SyncConfiguration config = builder.build();
        assertFalse(config.getRealmFileName().contains("?"));
    }

    @Test
    public void serverUrl_port() {
        Map<String, Integer> urlPort = new HashMap<String, Integer>();
        urlPort.put("realm://objectserver.realm.io/~/default", SyncConfiguration.PORT_REALM);
        urlPort.put("realms://objectserver.realm.io/~/default", SyncConfiguration.PORT_REALMS);
        urlPort.put("realm://objectserver.realm.io:8080/~/default", 8080);
        urlPort.put("realms://objectserver.realm.io:2443/~/default", 2443);

        for (String url : urlPort.keySet()) {
            SyncConfiguration config = new SyncConfiguration.Builder(createTestUser(), url).build();
            assertEquals(urlPort.get(url).intValue(), config.getServerUrl().getPort());
        }
    }

    @Test
    public void errorHandler() {
        SyncConfiguration.Builder builder = new SyncConfiguration.Builder(createTestUser(), "realm://objectserver.realm.io/default");
        Session.ErrorHandler errorHandler = new Session.ErrorHandler() {
            @Override
            public void onError(Session session, ObjectServerError error) {

            }
        };
        SyncConfiguration config = builder.errorHandler(errorHandler).build();
        assertEquals(errorHandler, config.getErrorHandler());
    }

    @Test
    public void errorHandler_fromSyncManager() {
        // Set default error handler
        Session.ErrorHandler errorHandler = new Session.ErrorHandler() {
            @Override
            public void onError(Session session, ObjectServerError error) {

            }
        };
        SyncManager.setDefaultSessionErrorHandler(errorHandler);

        // Create configuration using the default handler
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";
        SyncConfiguration config = new SyncConfiguration.Builder(user, url).build();
        assertEquals(errorHandler, config.getErrorHandler());
        SyncManager.setDefaultSessionErrorHandler(null);
    }


    @Test
    public void errorHandler_nullThrows() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";
        SyncConfiguration.Builder builder = new SyncConfiguration.Builder(user, url);

        try {
            builder.errorHandler(null);
        } catch (IllegalArgumentException ignore) {
        }
    }

    @Test
    public void equals() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";
        SyncConfiguration config = new SyncConfiguration.Builder(user, url)
                .build();
        assertTrue(config.equals(config));
    }

    @Test
    public void not_equals_same() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";
        SyncConfiguration config1 = new SyncConfiguration.Builder(user, url).build();
        SyncConfiguration config2 = new SyncConfiguration.Builder(user, url).build();

        assertFalse(config1.equals(config2));
    }

    @Test
    public void equals_not() {
        User user = createTestUser();
        String url1 = "realm://objectserver.realm.io/default1";
        String url2 = "realm://objectserver.realm.io/default2";
        SyncConfiguration config1 = new SyncConfiguration.Builder(user, url1).build();
        SyncConfiguration config2 = new SyncConfiguration.Builder(user, url2).build();
        assertFalse(config1.equals(config2));
    }

    @Test
    public void hashCode_equal() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";
        SyncConfiguration config = new SyncConfiguration.Builder(user, url)
                .build();

        assertEquals(config.hashCode(), config.hashCode());
    }

    @Test
    public void hashCode_notEquals() {
        User user = createTestUser();
        String url1 = "realm://objectserver.realm.io/default1";
        String url2 = "realm://objectserver.realm.io/default2";
        SyncConfiguration config1 = new SyncConfiguration.Builder(user, url1).build();
        SyncConfiguration config2 = new SyncConfiguration.Builder(user, url2).build();
        assertNotEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    public void get_syncSpecificValues() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";
        SyncConfiguration config = new SyncConfiguration.Builder(user, url).build();
        assertTrue(user.equals(config.getUser()));
        assertEquals("realm://objectserver.realm.io:80/default", config.getServerUrl().toString());
        assertFalse(config.shouldDeleteRealmOnLogout());
        assertTrue(config.isSyncConfiguration());
    }

    @Test
    public void encryption() {
       User user = createTestUser();
       String url = "realm://objectserver.realm.io/default";
       SyncConfiguration config = new SyncConfiguration.Builder(user, url)
               .encryptionKey(TestHelper.getRandomKey())
               .build();
       assertNotNull(config.getEncryptionKey());
    }

    @Test(expected = IllegalArgumentException.class)
    public void encryption_invalid_null() {
       User user = createTestUser();
       String url = "realm://objectserver.realm.io/default";

       new SyncConfiguration.Builder(user, url).encryptionKey(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encryption_invalid_wrong_length() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";

        new SyncConfiguration.Builder(user, url).encryptionKey(new byte[]{1, 2, 3});
    }

    @Test(expected = IllegalArgumentException.class)
    public void directory_null() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";
        new SyncConfiguration.Builder(user, url).directory(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void directory_writeProtectedDir() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";

        File dir = new File("/");
        new SyncConfiguration.Builder(user, url).directory(dir);
    }

    @Test
    public void directory_dirIsAFile() throws IOException {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";

        File dir = configFactory.getRoot();
        File file = new File(dir, "dummyfile");
        assertTrue(file.createNewFile());
        thrown.expect(IllegalArgumentException.class);
        new SyncConfiguration.Builder(user, url).directory(file);
        file.delete(); // clean up
    }

    @Test
    public void deleteOnLogout() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";

        SyncConfiguration config = new SyncConfiguration.Builder(user, url)
                .deleteRealmOnLogout()
                .build();
        assertTrue(config.shouldDeleteRealmOnLogout());
    }

    @Test
    public void initialData() {
        User user = createTestUser();
        String url = "realm://objectserver.realm.io/default";

        SyncConfiguration config = new SyncConfiguration.Builder(user, url)
                .initialData(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        StringOnly stringOnly = realm.createObject(StringOnly.class);
                        stringOnly.setChars("TEST 42");
                    }
                })
                .build();

        assertNotNull(config.getInitialDataTransaction());

        Realm realm = Realm.getInstance(config);
        RealmResults<StringOnly> results = realm.where(StringOnly.class).findAll();
        assertEquals(1, results.size());
        assertEquals("TEST 42", results.first().getChars());
        realm.close();
    }
}