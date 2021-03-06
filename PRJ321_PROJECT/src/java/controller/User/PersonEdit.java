/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controller.User;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import model.Log;
import model.User;
import service.ILogService;
import service.IPhotoService;
import service.IRoleService;
import service.IUserService;

/**
 *
 * @author ViruSs0209
 */
@MultipartConfig(fileSizeThreshold=1024*1024*2, // 2MB
                 maxFileSize=1024*1024*10,      // 10MB
                 maxRequestSize=1024*1024*50)
public class PersonEdit extends HttpServlet {
    @Inject
    IUserService db;
    
    @Inject
    IPhotoService photoDB;
    
    @Inject
    ILogService logDB;
    
    @Inject
    IRoleService roleDB;
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    public String generateUserId() {
        SecureRandom random = new SecureRandom();
        
        byte bytes[] = new byte[20];
        
        random.nextBytes(bytes);             
        
        UUID uuid = UUID.nameUUIDFromBytes(bytes);
         
        return uuid.toString();        
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {      
        String id = request.getParameter("id");
        
        request.setAttribute("user", db.getByID(id));
        request.setAttribute("currentMenu", "users");
        request.getRequestDispatcher("view/user/userEdit.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String id = request.getParameter("id");
        String firstName = request.getParameter("firstName");
        String role = request.getParameter("role");
        String lastName = request.getParameter("lastName");
        String country = request.getParameter("country");
        int age = Integer.parseInt(request.getParameter("age"));    
        
        boolean gender = false;
        
        if (request.getParameter("gender").equals("Male")) {
            gender = true;
        }
        
        User currentUser = (User) request.getSession().getAttribute("user");

        User updatedUser = db.getByID(id);
        // Create Log User For What They Updated
        JsonObject jsonObject = new JsonObject();
        
        if (currentUser.getRole().equals("manager")) {
            jsonObject.addProperty("ManagerID", currentUser.getUserID());
        } else {
            jsonObject.addProperty("EmployeeID", currentUser.getUserID());
        }   
        
        jsonObject.addProperty("UpdatedUser", updatedUser.getUserID());
        
        if (!updatedUser.getFirstName().equals(firstName)) {
            jsonObject.addProperty("firstName", firstName);
        }
        
        if (!updatedUser.getLastName().equals(lastName)) {
            jsonObject.addProperty("lastName", lastName);
        }
        
        if (!updatedUser.getCountry().equals(country)) {
            jsonObject.addProperty("country", country);
        }
        
        if (updatedUser.getAge() != age) {
            jsonObject.addProperty("age", age);
        } 
        
        if (!updatedUser.getRole().equals(role)) {
            jsonObject.addProperty("role", role);
        } 
        
        if (gender != updatedUser.isGender()) {
            if (gender) {
                jsonObject.addProperty("gender", "Male");
            } else {
                jsonObject.addProperty("gender", "Feamle");
            }
        }

         // Set What Updated For User
        updatedUser.setFirstName(firstName);
        updatedUser.setLastName(lastName);
        updatedUser.setCountry(country);
        updatedUser.setAge(age);
        updatedUser.setGender(gender);
        
        if (role != null) {
            updatedUser.setRole(role);
        } 
     
        try {
            DateFormat dateFormatOutput = new SimpleDateFormat("yyyy-MM-dd HH:mm");                
            Date now = new Date(System.currentTimeMillis());

            String formattedDate = dateFormatOutput.format(now);
            Date currentDate = dateFormatOutput.parse(formattedDate);
            
            Timestamp currentTime = new Timestamp(currentDate.getTime());
            
            updatedUser.setUpdatedAt(currentTime);
            
            Part filePart = request.getPart("avatar"); // Retrieves <input type="file" name="file">     
            String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.

            InputStream fileContent = null;

            String imageID = null;
            // Check If User Haven't Update Any Images
            if (!fileName.equals("")) {
                
                if (updatedUser.getAvatar() != null) {
                    photoDB.deletePhotoById(updatedUser.getAvatar());
                }
                
                imageID = generateUserId();
                
                fileContent = filePart.getInputStream();
                
                updatedUser.setAvatar(imageID);                         
                jsonObject.addProperty("avatar", imageID);
                
                photoDB.insert(fileContent, "User", imageID, currentTime);
            }             
            // Update Role For User If Needed
            roleDB.update(id, role);
            db.updateUser(updatedUser);
            // Create Log For Update Action
            Log log = new Log();
            
            Gson jsonConverter = new Gson();            
            
            log.setUserEmail(currentUser.getEmail());
            log.setEntity("User");
            log.setEntityID(updatedUser.getUserID());
            log.setAction("Updated");                
            log.setContent(jsonObject.toString());
            log.setCreatedAt(currentTime);
            
            logDB.insert(log);
            
            
            if (updatedUser.getUserName() == currentUser.getUserName()) {
                request.getSession().setAttribute("user", updatedUser); 
            }
            
            if (currentUser.getRole().equals("manager") || currentUser.getRole().equals("employee")) {
                request.getSession().setAttribute("updatePerson", "Update Person Successfully !");
                response.sendRedirect("/users");
            } else {
                request.setAttribute("currentMenu", "bookings");
                request.getRequestDispatcher("view/bookings.jsp").forward(request, response);
            }
            
        } catch (ParseException ex) {
            request.setAttribute("fail", "Update Profile Unsuccessfully !");            
            
            if (currentUser.getRole().equals("manager")) {
                request.setAttribute("currentMenu", "users");
                request.getRequestDispatcher("view/users.jsp").forward(request, response);
            } else {
                request.setAttribute("currentMenu", "bookings");
                request.getRequestDispatcher("view/bookings.jsp").forward(request, response);
            }
        } 
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
