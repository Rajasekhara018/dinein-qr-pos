Identity
──────────────
✅ User
✅ UserCredential
✅ UserLoginSecurity
✅ PasswordHistory
✅ UserSession
✅ UserDevice
✅ UserMfa
✅ ApiClient

Authorization
──────────────
✅ Permission
✅ Role
✅ RolePermission
✅ Group
✅ UserGroup
✅ GroupRole

Application
──────────────
✅ Tenant
✅ Application
✅ Module
✅ Resource
✅ PermissionAction

Security
──────────────
✅ SecurityPolicy
✅ AuditLog
✅ RefreshToken

Communication
──────────────
✅ NotificationTemplate
✅ NotificationPreference

Feature Management
──────────────
✅ FeatureFlag
✅ FeatureAssignment


# Bootstrap Flow


1. Create Permissions

↓

2. Create Super Admin Role

↓

3. Assign all permissions to Super Admin Role

↓

4. Create Super Admin Group

↓

5. Assign Super Admin Role to Group

↓

6. Create Super Admin User

↓

7. Assign User to Super Admin Group



# Login Flow

admin

↓

Find User

↓

Find User Groups

↓

Find Roles

↓

Find Permissions

↓

JWT