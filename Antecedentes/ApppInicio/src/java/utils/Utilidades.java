package utils;

import java.util.HashMap;
import mx.edu.ittoluca.ittlogin.commons.security.Decrypter;
import pojo.Constantes;
//import mx.edu.ittoluca.ittlogin.commons.security.Decrypter;
import pojo.Usuario;

public class Utilidades {

    public static Usuario validateCredentials(String token) {
        Usuario user = null;

        if (token != null) {
            Decrypter decrypter = new Decrypter();
            HashMap<String, String> credentials = decrypter.decryptAndValidate(token, Constantes.NOMBRE_APP, Constantes.NOMBRE_MODULO);

            if (credentials != null) {
                user = new Usuario();
                user.setUsername(credentials.get("username"));
                user.setPassword(credentials.get("password"));
                user.setAuth(true);
            }
        }

        return user;
    }

}
