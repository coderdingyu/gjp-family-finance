<template>
  <div class="login-page" :class="{ 'is-dark': isDark }">
    <div class="ambient" aria-hidden="true">
      <span class="blob blob-a" />
      <span class="blob blob-b" />
      <span class="blob blob-c" />
      <span class="mesh" />
    </div>

    <button
      type="button"
      class="theme-toggle"
      :title="isDark ? '切换日间模式' : '切换夜间模式'"
      :aria-label="isDark ? '切换日间模式' : '切换夜间模式'"
      @click="onToggleTheme"
    >
      <el-icon :size="18"><Moon v-if="!isDark" /><Sunny v-else /></el-icon>
    </button>

    <div class="login-shell">
      <div class="brand">
        <div class="brand-mark">
          <div class="brand-badge">管</div>
          <span class="brand-orb" aria-hidden="true" />
        </div>
        <h1>管家婆</h1>
        <p>家庭收支管理系统</p>
        <ul>
          <li>收支流水多维度录入，商家、片区、人情往来一次记全</li>
          <li>月度年度统计报表，分类占比与收支趋势一目了然</li>
          <li>智能分析给出结论：哪个月超支、超在哪、是偶发还是会持续</li>
          <li>三级分类、账单查重、操作日志，权限按户主与成员分级</li>
        </ul>
      </div>

      <el-card class="login-card" shadow="never">
        <div class="card-accent" aria-hidden="true" />
        <el-tabs v-model="tab" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form ref="loginRef" :model="loginForm" :rules="loginRules" label-position="top" size="large">
              <el-form-item label="账号" prop="username">
                <el-input v-model="loginForm.username" placeholder="请输入账号" :prefix-icon="User" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  show-password
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  @keyup.enter="onLogin"
                />
              </el-form-item>
              <el-button type="primary" class="submit" :loading="loading" @click="onLogin">登 录</el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册新家庭" name="register">
            <el-form ref="regRef" :model="regForm" :rules="regRules" label-position="top" size="large">
              <el-form-item label="家庭名称" prop="familyName">
                <el-input v-model="regForm.familyName" placeholder="如：张家" />
              </el-form-item>
              <el-form-item label="您的姓名" prop="realName">
                <el-input v-model="regForm.realName" placeholder="将自动成为家庭第一位成员" />
              </el-form-item>
              <el-form-item label="账号" prop="username">
                <el-input v-model="regForm.username" placeholder="3-20 个字符" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="regForm.password" type="password" show-password placeholder="6-20 个字符" />
              </el-form-item>
              <el-button type="primary" class="submit" :loading="loading" @click="onRegister">注册并登录</el-button>
              <p class="tip">注册后系统会自动为该家庭初始化 12 类、40 余项常用收支分类，可直接开始记账。</p>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, Moon, Sunny, User } from '@element-plus/icons-vue'
import { login, register } from '../../api/auth'
import { ROLE, setUser } from '../../utils/auth'
import { setToken } from '../../utils/authToken'
import { getStoredTheme, toggleTheme } from '../../utils/theme'

const tab = ref('login')
const loading = ref(false)
const isDark = ref(getStoredTheme() === 'dark')

const loginRef = ref()
const loginForm = ref({ username: '', password: '' })
const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const regRef = ref()
const regForm = ref({ familyName: '', realName: '', username: '', password: '' })
const regRules = {
  familyName: [{ required: true, message: '请输入家庭名称', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入您的姓名', trigger: 'blur' }],
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 20, message: '账号长度 3-20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 个字符', trigger: 'blur' }
  ]
}

function onToggleTheme() {
  isDark.value = toggleTheme() === 'dark'
}

onMounted(() => {
  isDark.value = document.documentElement.classList.contains('dark')
})

function enter(result) {
  // token 先存下来，它决定本标签页之后以哪个身份发请求；顺序反了会先发出一次无身份的请求。
  const user = result.user
  setToken(result.token)
  setUser(user)
  ElMessage.success(`欢迎回来，${user.realName || user.username}`)
  // 整页进入首页，确保这个标签页上一个账号的组件状态、请求和轮询全部销毁。
  // 只影响本标签页：别的标签页的身份存在各自的 sessionStorage 里，不受这里影响。
  window.location.replace(user.role === ROLE.ADMIN ? '/admin' : '/home')
}

async function onLogin() {
  await loginRef.value.validate()
  loading.value = true
  try {
    enter(await login(loginForm.value))
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  await regRef.value.validate()
  loading.value = true
  try {
    enter(await register(regForm.value))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background:
    radial-gradient(1200px 600px at 10% -10%, rgba(99, 102, 241, 0.14), transparent 55%),
    radial-gradient(900px 500px at 90% 110%, rgba(129, 140, 248, 0.12), transparent 50%),
    var(--gjp-bg, #f8fafc);
}

.ambient {
  pointer-events: none;
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.55;
}

.blob-a {
  width: 320px;
  height: 320px;
  left: -60px;
  top: 12%;
  background: rgba(99, 102, 241, 0.35);
}

.blob-b {
  width: 260px;
  height: 260px;
  right: -40px;
  bottom: 8%;
  background: rgba(129, 140, 248, 0.28);
}

.blob-c {
  width: 180px;
  height: 180px;
  left: 42%;
  top: -40px;
  background: rgba(165, 180, 252, 0.35);
}

.mesh {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(135deg, rgba(248, 250, 252, 0.35) 0%, transparent 40%),
    linear-gradient(0deg, transparent 60%, rgba(238, 242, 255, 0.45) 100%);
  opacity: 0.9;
}

.theme-toggle {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
  color: var(--gjp-primary, #4f46e5);
  cursor: pointer;
  box-shadow: var(--gjp-soft-shadow);
  transition: transform 0.15s ease, background 0.15s ease;
}

.theme-toggle:hover {
  transform: translateY(-1px);
  background: rgba(238, 242, 255, 0.95);
}

.login-shell {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 72px;
  width: min(980px, 100%);
}

.brand {
  color: var(--gjp-text);
  max-width: 420px;
}

.brand-mark {
  position: relative;
  display: inline-flex;
  margin-bottom: 16px;
}

.brand-badge {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: linear-gradient(145deg, #6366f1, #4f46e5 55%, #4338ca);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  box-shadow: 0 14px 28px -10px rgba(79, 70, 229, 0.55);
  position: relative;
  z-index: 1;
}

.brand-orb {
  position: absolute;
  width: 28px;
  height: 28px;
  right: -14px;
  bottom: -8px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, #c7d2fe, #818cf8 55%, transparent 70%);
  filter: blur(0.2px);
  opacity: 0.9;
}

.brand h1 {
  font-size: 40px;
  margin: 0;
  letter-spacing: 6px;
  color: var(--gjp-text);
}

.brand p {
  font-size: 16px;
  color: var(--gjp-text-muted);
  margin: 8px 0 22px;
  letter-spacing: 2px;
}

.brand ul {
  padding-left: 18px;
  line-height: 2;
  color: var(--gjp-text-muted);
  font-size: 14px;
}

.brand li::marker {
  color: #4f46e5;
}

.login-card {
  position: relative;
  width: 420px;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.85) !important;
  border-radius: 28px !important;
  background: rgba(255, 255, 255, 0.78) !important;
  backdrop-filter: blur(18px);
  box-shadow:
    0 20px 50px -18px rgba(148, 163, 184, 0.45),
    inset 0 1px 0 rgba(255, 255, 255, 0.7) !important;
}

.card-accent {
  position: absolute;
  top: -40px;
  right: -30px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.22), transparent 70%);
  pointer-events: none;
}

.login-card :deep(.el-card__body) {
  padding: 30px 30px 26px;
  position: relative;
  z-index: 1;
}

.login-card :deep(.el-tabs__item.is-active) {
  color: #4f46e5;
  font-weight: 600;
}

.login-card :deep(.el-tabs__active-bar) {
  background-color: #4f46e5;
}

.login-card :deep(.el-tabs__nav-wrap::after) {
  background-color: var(--gjp-border);
}

.submit {
  width: 100%;
  margin-top: 4px;
  height: 46px;
  font-size: 15px;
  border-radius: 14px;
  background: linear-gradient(135deg, #6366f1, #4f46e5) !important;
  border-color: #4f46e5 !important;
  box-shadow: 0 12px 28px -8px rgba(79, 70, 229, 0.5);
}

.submit:hover,
.submit:focus {
  background: linear-gradient(135deg, #4f46e5, #4338ca) !important;
  border-color: #4338ca !important;
}

.tip {
  font-size: 12px;
  color: var(--gjp-text-light);
  line-height: 1.7;
  margin: 12px 0 0;
}

@media (max-width: 900px) {
  .brand {
    display: none;
  }
  .login-card {
    width: min(420px, 100%);
  }
  .login-shell {
    gap: 0;
  }
}
</style>

<style>
/* Dark Soft UI login — unscoped */
html.dark .login-page,
.login-page.is-dark {
  background:
    radial-gradient(1000px 520px at 12% 0%, rgba(99, 102, 241, 0.22), transparent 55%),
    radial-gradient(800px 480px at 92% 100%, rgba(67, 56, 202, 0.28), transparent 50%),
    #0f172a !important;
}
html.dark .blob-a {
  background: rgba(99, 102, 241, 0.45) !important;
  opacity: 0.7;
}
html.dark .blob-b {
  background: rgba(67, 56, 202, 0.4) !important;
  opacity: 0.65;
}
html.dark .blob-c {
  background: rgba(129, 140, 248, 0.35) !important;
}
html.dark .mesh {
  background:
    linear-gradient(160deg, rgba(15, 23, 42, 0.2) 0%, transparent 45%),
    linear-gradient(0deg, transparent 50%, rgba(30, 41, 59, 0.55) 100%) !important;
}
html.dark .theme-toggle {
  border-color: rgba(71, 85, 105, 0.9) !important;
  background: rgba(30, 41, 59, 0.75) !important;
  color: #a5b4fc !important;
}
html.dark .theme-toggle:hover {
  background: rgba(51, 65, 85, 0.95) !important;
}
html.dark .brand li::marker {
  color: #818cf8 !important;
}
html.dark .login-card {
  border-color: rgba(71, 85, 105, 0.85) !important;
  background: rgba(30, 41, 59, 0.72) !important;
  box-shadow:
    0 24px 60px -20px rgba(0, 0, 0, 0.65),
    inset 0 1px 0 rgba(255, 255, 255, 0.06) !important;
}
html.dark .card-accent {
  background: radial-gradient(circle, rgba(129, 140, 248, 0.28), transparent 70%) !important;
}
html.dark .login-card .el-tabs__item {
  color: #94a3b8 !important;
}
html.dark .login-card .el-tabs__item.is-active {
  color: #a5b4fc !important;
}
html.dark .login-card .el-tabs__active-bar {
  background-color: #818cf8 !important;
}
html.dark .submit {
  background: linear-gradient(135deg, #818cf8, #6366f1) !important;
  border-color: #6366f1 !important;
  box-shadow: 0 14px 30px -10px rgba(99, 102, 241, 0.55);
}
</style>
