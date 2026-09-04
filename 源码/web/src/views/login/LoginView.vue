<template>
  <div class="login-page">
    <div class="brand">
      <h1>管家婆</h1>
      <p>家庭收支管理系统</p>
      <ul>
        <li>收支流水多维度录入，商家、片区、人情往来一次记全</li>
        <li>月度年度统计报表，分类占比与收支趋势一目了然</li>
        <li>智能分析给出结论：哪个月超支、超在哪、是偶发还是会持续</li>
        <li>三级分类、账单查重、操作日志，权限按户主与成员分级</li>
      </ul>
    </div>

    <el-card class="login-card">
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
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { login, register } from '../../api/auth'
import { ROLE, setUser } from '../../utils/auth'
import { setToken } from '../../utils/authToken'

const tab = ref('login')
const loading = ref(false)

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
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80px;
  background:
    radial-gradient(1000px 520px at 15% 10%, #e0e7ff 0%, transparent 55%),
    radial-gradient(800px 480px at 90% 90%, #fce7f3 0%, transparent 50%),
    var(--gjp-bg);
}

.brand {
  color: var(--gjp-text);
  max-width: 420px;
}

.brand h1 {
  font-size: 44px;
  margin: 0;
  letter-spacing: 6px;
  color: var(--gjp-primary);
}

.brand p {
  font-size: 18px;
  color: #64748b;
  margin: 8px 0 26px;
  letter-spacing: 2px;
}

.brand ul {
  padding-left: 20px;
  line-height: 2.1;
  color: #64748b;
  font-size: 14px;
}

.login-card {
  width: 380px;
  border-radius: var(--gjp-radius);
  border: none;
}

.submit {
  width: 100%;
  margin-top: 4px;
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
}
</style>
