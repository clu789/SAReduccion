package pojo;

public class Usuario {

    private static String username = "";
    private static String password = "";
    private static boolean auth = false;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        Usuario.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        Usuario.password = password;
    }

    public boolean getAuth() {
        return auth;
    }

    public static boolean isAuth() {
        return auth;
    }

    public static void setAuth(boolean auth) {
        Usuario.auth = auth;
    }

}
