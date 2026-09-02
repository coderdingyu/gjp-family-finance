import { computed, ref } from 'vue'

/**
 * 登录态与权限判断。
 *
 * 用一个模块级的 ref 而不是 Pinia：本项目只需要共享"当前登录用户"这一个状态，
 * 引入状态管理库反而增加理解成本。ref 定义在模块作用域，所有 import 的地方拿到同一份。
 *
 * 前端的角色判断只用于**控制界面显隐**，真正的权限校验在后端。
 * 二者都有：后端保证安全，前端避免用户看到点了就报 403 的按钮。
 */
const KEY = 'gjp_user'

const user = ref(JSON.parse(sessionStorage.getItem(KEY) || '{}'))

/** 角色常量，与后端 com.gjp.common.Role 保持一致 */
export const ROLE = {
  MEMBER: 0,
  OWNER: 1,
  ADMIN: 2
}

export function setUser(data) {
  user.value = data || {}
  if (data) {
    sessionStorage.setItem(KEY, JSON.stringify(data))
  } else {
    sessionStorage.removeItem(KEY)
  }
}

export function clearUser() {
  setUser(null)
}

export const currentUser = computed(() => user.value)
export const isLogged = computed(() => !!user.value.userId)
export const role = computed(() => user.value.role ?? ROLE.MEMBER)
export const isAdmin = computed(() => role.value === ROLE.ADMIN)
export const isOwner = computed(() => role.value === ROLE.OWNER)
/** 家庭内角色（普通成员或户主），系统管理员不算 */
export const isFamilyUser = computed(() => role.value === ROLE.MEMBER || role.value === ROLE.OWNER)
/** 数据范围是否被锁定到自己 */
export const scopeLocked = computed(() => role.value === ROLE.MEMBER)

export default {
  ROLE,
  setUser,
  clearUser,
  currentUser,
  isLogged,
  role,
  isAdmin,
  isOwner,
  isFamilyUser,
  scopeLocked
}
