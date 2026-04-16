package com.sunlunch.sunlunch.controller;

import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
public class AuthController {

   private final UserRepository userRepository;
   public AuthController(UserRepository userRepository){
       this.userRepository=userRepository;
   }
@GetMapping("/register")
    public String registerPage(){
       return"register";
}
@PostMapping("/register")
    public String registerUser(@RequestParam("email") String email,
                               @RequestParam("name") String name,
                               @RequestParam("password") String password,
                               Model model){
       User existingUser = userRepository.findByEmail(email);
       if(existingUser!=null){
           model.addAttribute("error","このメールアドレスは既に登録されています。");
           return "register";
       }
       User user = new User();
       user.setEmail(email);
       user.setName(name);
       user.setPassword(password);
       user.setRole("USER");
       user.setDeleted(false);

       userRepository.save(user);
       model.addAttribute("message","登録が完了しました。");
       return "redirect:/login?success";
}
@GetMapping("/login")
    public String loginPage(){
       return "login";
}
@PostMapping("/login")
    public String loginUser(@RequestParam("email") String email,
                            @RequestParam("password") String password,
                            Model model,
                            HttpSession session) {
    User user = userRepository.findByEmailAndPassword(email, password);
    if (user == null) {
        model.addAttribute("error", "メールアドレスまたはパスワードが正しくありません。");
        return "login";
    }
    if ("ADMIN".equals(user.getRole())) {
        model.addAttribute("error", "管理员账号请从管理员登录入口登录。");
        return "login";
    }
    session.setAttribute("loginUser",user);
    return "redirect:/home";
}
@GetMapping("/admin/login")
    public String adminLoginPage(HttpSession session){
       User loginUser = (User) session.getAttribute("loginUser");
       if(loginUser != null && "ADMIN".equals(loginUser.getRole())){
           return "redirect:/admin/orders/today";
       }
       return "admin-login";
}
@PostMapping("/admin/login")
    public String adminLoginUser(@RequestParam("email") String email,
                                 @RequestParam("password") String password,
                                 Model model,
                                 HttpSession session) {
    User user = userRepository.findByEmailAndPassword(email, password);
    if (user == null || !"ADMIN".equals(user.getRole())) {
        model.addAttribute("error", "管理员账号或密码不正确。");
        return "admin-login";
    }

    session.setAttribute("loginUser", user);
    return "redirect:/home";
}
@GetMapping("/home")
    public String homePage(HttpSession session,Model model, HttpServletResponse response){
       User loginUser = (User) session.getAttribute("loginUser");

       if(loginUser ==null){
           return "redirect:/login";
       }
       applyNoCacheHeaders(response);
       model.addAttribute("user",loginUser);
       return "home";
}
@GetMapping("/logout")
    public String logout(HttpSession session){
       session.invalidate();
       return "redirect:/login";
}

private void applyNoCacheHeaders(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    response.setHeader("Pragma", "no-cache");
    response.setDateHeader("Expires", 0);
}

@GetMapping("/profile")
    public String profilePage(HttpSession session,
                              Model model){
       User loginUser = (User) session.getAttribute("loginUser");

       if(loginUser == null){
           return "redirect:/login";
       }

       model.addAttribute("user",loginUser);
       return "profile";
}
@PostMapping("/profile")
    public String updateProfile(@RequestParam("name") String name,
                                HttpSession session,
                                Model model){
       User loginUser = (User) session.getAttribute("loginUser");

       if(loginUser == null){
           return "redirect:/login";
       }
       User user = userRepository.findById(loginUser.getId()).orElse(null);
       if(user == null){
           return "redirect:/login";
       }

       user.setName(name);
       userRepository.save(user);
       session.setAttribute("loginUser",user);

       model.addAttribute("user",user);
       model.addAttribute("message","プロフィールを更新しました。");
       return "profile";
}
@PostMapping("/profile/password")
    public String changePassword(@RequestParam("oldPassword") String oldPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 HttpSession session,
                                 Model model){
       User loginUser = (User) session.getAttribute("loginUser");
       if(loginUser == null){
           return "redirect:/login";
       }

       User user = userRepository.findById(loginUser.getId()).orElse(null);

       if(user == null){
           return "redirect:/login";
       }

       if(!user.getPassword().equals(oldPassword)){
           model.addAttribute("user",user);
           model.addAttribute("error","現在のパスワードが正しくありません。");
           return "profile";
       }

       user.setPassword(newPassword);
       userRepository.save(user);

       session.setAttribute("loginUser",user);

       model.addAttribute("user",user);
       model.addAttribute("passwordMessage","パスワードを更新しました。");
       return "profile";
}
@GetMapping("/forgot-password")
    public String forgotPasswordPage(){
       return "forgot-password";
}

@PostMapping("/forgot-password")
    public String resetPassword(@RequestParam("email") String email,
                                @RequestParam("token") String token,
                                @RequestParam("newPassword") String newPassword,
                                Model model){
       User user = userRepository.findByEmail(email);
       if(user == null){
           model.addAttribute("error","メールアドレスが見つかりません。");
           return "forgot-password";
       }

       if(user.getResetToken()==null || !user.getResetToken().equals(token)){
        model.addAttribute("error", "認証コードが正しくありません");
        return "forgot-password";
       }

       user.setPassword(newPassword);
       user.setResetToken(null);
       userRepository.save(user);

       model.addAttribute("message","パスワードのリセットが完了しました。ログインしてください。");
       return "forgot-password";
}

    @PostMapping("/generate-token")
    public String generateToken(@RequestParam("email") String email,Model model) {
        User user = userRepository.findByEmail(email);
        model.addAttribute("email",email);
        if(user == null){
            model.addAttribute("error","メールアドレスが存在しません。");
            return "forgot-password";
        }

        String token = java.util.UUID.randomUUID().toString().substring(0,6);
        user.setResetToken(token);
        userRepository.save(user);
        model.addAttribute("message", "隱崎ｨｼ繧ｳ繝ｼ繝・ " + token);
        return "forgot-password";
    }
    

    
    
   


}

