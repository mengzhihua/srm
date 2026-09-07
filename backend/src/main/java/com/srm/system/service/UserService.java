package com.srm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.common.BizException;
import com.srm.system.auth.PasswordHasher;
import com.srm.system.auth.TokenService;
import com.srm.system.entity.User;
import com.srm.system.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements ApplicationRunner {
    private static final List<String> ROLES = Arrays.asList(User.ADMIN, User.BUYER, User.SUPPLIER, User.VIEWER);

    private final UserMapper userMapper;
    private final TokenService tokenService;

    @Value("${srm.auth.admin-password:admin123}")
    private String initialAdminPassword;

    /** 首次启动无任何用户时创建演示账号（口令来自 srm.auth.admin-password / SRM_ADMIN_PASSWORD） */
    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        createUser("admin", initialAdminPassword, "系统管理员", User.ADMIN, null);
        createUser("buyer", "buyer123", "采购员", User.BUYER, null);
        createUser("sup01", "sup123", "深圳精密电子厂", User.SUPPLIER, "SUP01");
        log.info("已初始化演示账号 admin / buyer / sup01");
    }

    private void createUser(String username, String password, String realName, String role, String supplierCode) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(PasswordHasher.hash(password));
        u.setRealName(realName);
        u.setRole(role);
        u.setSupplierCode(supplierCode);
        u.setStatus(1);
        userMapper.insert(u);
    }

    @Data
    public static class LoginResult {
        private String token;
        private User user;
    }

    @Transactional
    public LoginResult login(String username, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !PasswordHasher.verify(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        LoginResult r = new LoginResult();
        r.setToken(tokenService.issue(user.getId(), user.getUsername()));
        r.setUser(user);
        return r;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null || !PasswordHasher.verify(oldPassword, user.getPassword())) {
            throw new BizException("原密码错误");
        }
        User upd = new User();
        upd.setId(userId);
        upd.setPassword(PasswordHasher.hash(requireStrong(newPassword)));
        userMapper.updateById(upd);
    }

    /** 管理员维护：新增必须带密码，修改时密码为空表示不改 */
    public void prepareForSave(User entity, boolean creating) {
        if (entity.getUsername() == null || entity.getUsername().trim().isEmpty()) {
            throw new BizException("用户名不能为空");
        }
        if (!ROLES.contains(entity.getRole())) {
            throw new BizException("角色必须为 " + ROLES);
        }
        if (User.SUPPLIER.equals(entity.getRole())
                && (entity.getSupplierCode() == null || entity.getSupplierCode().trim().isEmpty())) {
            throw new BizException("供应商账号必须绑定 supplierCode");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setLastLoginAt(null);
        if (entity.getPassword() != null && !entity.getPassword().isEmpty()) {
            entity.setPassword(PasswordHasher.hash(requireStrong(entity.getPassword())));
        } else if (creating) {
            throw new BizException("新建用户必须设置密码");
        }
    }

    public void ensureNotLastAdmin(Long userId, User incoming) {
        User existing = userMapper.selectById(userId);
        if (existing == null || !User.ADMIN.equals(existing.getRole())) {
            return;
        }
        boolean demoted = incoming == null
                || !User.ADMIN.equals(incoming.getRole())
                || (incoming.getStatus() != null && incoming.getStatus() != 1);
        if (!demoted) {
            return;
        }
        Long admins = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, User.ADMIN).eq(User::getStatus, 1).ne(User::getId, userId));
        if (admins == 0) {
            throw new BizException("至少保留一个启用的管理员账号");
        }
    }

    private static String requireStrong(String pwd) {
        if (pwd == null || pwd.length() < 6) {
            throw new BizException("密码长度至少 6 位");
        }
        return pwd;
    }
}
