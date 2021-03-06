package demo;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Scanner;

/**
 * Handles the front end for the demo.
 */
public class PropertyFrontEndHandler extends AbstractHandler {
    // Location of the index folder relative to the location from which the program is run
    private final String indexFolderLocation;

    public PropertyFrontEndHandler(String indexFolderLocation) {
        this.indexFolderLocation = indexFolderLocation;
    }

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        PrintWriter out = response.getWriter();
        response.setContentType("text/html");
        // Writes the html form to response
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream indexForm = classLoader.getResourceAsStream("propertyForm.html");
        out.write(new Scanner(indexForm).useDelimiter("\\A").next());
        baseRequest.setAttribute("format", "HTML");

        // Writes the properties to response
        new PropertyHandler(indexFolderLocation).handle(target, baseRequest, request, response);
        baseRequest.setHandled(true);

    }
}
