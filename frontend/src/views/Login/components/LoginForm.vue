<template>
  <el-form
    v-show="getShow"
    ref="formLogin"
    :model="loginData.loginForm"
    :rules="LoginRules"
    class="login-form"
    label-position="top"
    label-width="120px"
    size="large"
  >
    <el-row class="mx-[-10px]">
      <el-col :span="24" class="px-10px">
        <el-form-item>
          <LoginFormTitle class="w-full" />
        </el-form-item>
      </el-col>
      <el-col :span="24" class="px-10px">
        <el-form-item v-if="loginData.tenantEnable === 'true'" prop="tenantName">
          <el-input
            v-model="loginData.loginForm.tenantName"
            :placeholder="t('login.tenantNamePlaceholder')"
            :prefix-icon="iconHouse"
            link
            type="primary"
          />
        </el-form-item>
      </el-col>
      <el-col :span="24" class="px-10px">
        <el-form-item prop="username">
          <el-input
            v-model="loginData.loginForm.username"
            :placeholder="t('login.usernamePlaceholder')"
            :prefix-icon="iconAvatar"
            autocomplete="off"
          />
          <div class="dica-login">O mesmo usuário da rede (ex.: nome.sobrenome)</div>
        </el-form-item>
      </el-col>
      <el-col :span="24" class="px-10px">
        <el-form-item prop="password">
          <el-input
            v-model="loginData.loginForm.password"
            :placeholder="t('login.passwordPlaceholder')"
            :prefix-icon="iconLock"
            autocomplete="new-password"
            show-password
            type="password"
            @blur="capsLock = false"
            @keydown="verCapsLock"
            @keyup="verCapsLock"
            @keyup.enter="getCode()"
          />
          <div v-if="capsLock" class="dica-login dica-login--alerta">
            <Icon icon="ep:warning-filled" :size="13" /> Caps Lock ligado
          </div>
        </el-form-item>
      </el-col>
      <el-col :span="24" class="px-10px mt-[-20px] mb-[-20px]">
        <el-form-item>
          <div class="w-full flex items-center justify-between">
            <el-checkbox v-model="loginData.loginForm.rememberMe">
              {{ t('login.remember') }}
            </el-checkbox>
            <el-link
              v-if="assistente"
              :underline="false"
              type="primary"
              @click="assistenteAberto = true"
            >
              Esqueci minha senha
            </el-link>
          </div>
        </el-form-item>
      </el-col>
      <el-col :span="24" class="px-10px">
        <el-form-item>
          <el-button :loading="loginLoading" class="w-full" type="primary" @click="getCode()">
            {{ t('login.login') }}
          </el-button>
        </el-form-item>
      </el-col>
      <Verify
        v-if="loginData.captchaEnable === 'true'"
        ref="verify"
        :captchaType="captchaType"
        :imgSize="{ width: '400px', height: '200px' }"
        mode="pop"
        @success="handleLogin"
      />
    </el-row>
  </el-form>
  <AssistenteSenha v-if="assistente" v-model="assistenteAberto" />
  <!-- CO-TI: segunda etapa (verificação em duas etapas) -->
  <el-form
    v-if="mfa.desafio"
    ref="formMfa"
    :model="mfa"
    class="login-form"
    label-position="top"
    size="large"
    @submit.prevent
  >
    <el-form-item>
      <LoginFormTitle class="w-full" />
    </el-form-item>
    <p class="mb-16px text-[var(--el-text-color-regular)]">{{ t('coti.mfa.instrucaoLogin') }}</p>
    <el-form-item
      prop="codigo"
      :rules="[{ required: true, message: t('coti.mfa.codigoObrigatorio') }]"
    >
      <el-input
        ref="campoCodigoMfa"
        v-model="mfa.codigo"
        :placeholder="t('coti.mfa.codigoPlaceholder')"
        :prefix-icon="iconLock"
        autocomplete="one-time-code"
        inputmode="numeric"
        maxlength="20"
        @keyup.enter="enviarCodigoMfa()"
      />
    </el-form-item>
    <el-form-item>
      <el-button :loading="loginLoading" class="w-full" type="primary" @click="enviarCodigoMfa()">
        {{ t('coti.mfa.verificar') }}
      </el-button>
    </el-form-item>
    <el-form-item>
      <el-link type="primary" @click="cancelarMfa()">{{ t('coti.mfa.voltar') }}</el-link>
    </el-form-item>
  </el-form>
</template>
<script lang="ts" setup>
import { ElLoading } from 'element-plus'
import LoginFormTitle from './LoginFormTitle.vue'
import AssistenteSenha from './AssistenteSenha.vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { assistenteDisponivel } from '@/api/atendimento/widget'

import { useIcon } from '@/hooks/web/useIcon'

import * as authUtil from '@/utils/auth'
import { usePermissionStore } from '@/store/modules/permission'
import * as LoginApi from '@/api/login'
import { LoginStateEnum, useFormValid, useLoginState } from './useLogin'

defineOptions({ name: 'LoginForm' })

const { t } = useI18n()
const iconHouse = useIcon({ icon: 'ep:house' })
const iconAvatar = useIcon({ icon: 'ep:avatar' })
const iconLock = useIcon({ icon: 'ep:lock' })
const formLogin = ref()
const { validForm } = useFormValid(formLogin)
const { getLoginState } = useLoginState()
const { currentRoute, push } = useRouter()
const permissionStore = usePermissionStore()
const redirect = ref<string>('')
const loginLoading = ref(false)
const verify = ref()
const captchaType = ref('blockPuzzle') // blockPuzzle 滑块 clickWord 点击文字 pictureWord 文字验证码

const getShow = computed(() => unref(getLoginState) === LoginStateEnum.LOGIN && !mfa.desafio)

// CO-TI: "Esqueci minha senha" abre o assistente (some se a caixa da tela de login estiver desativada)
const assistente = ref(false)
const assistenteAberto = ref(false)
const capsLock = ref(false)
const verCapsLock = (e: KeyboardEvent) => {
  capsLock.value = !!e.getModifierState?.('CapsLock')
}

// CO-TI: login em duas etapas
const formMfa = ref()
const campoCodigoMfa = ref()
const mfa = reactive({ desafio: '', codigo: '', formulario: null as any })

const LoginRules = {
  tenantName: [required],
  username: [required],
  password: [required]
}
const loginData = reactive({
  isShowPassword: false,
  captchaEnable: import.meta.env.VITE_APP_CAPTCHA_ENABLE,
  tenantEnable: import.meta.env.VITE_APP_TENANT_ENABLE,
  loginForm: {
    tenantName: import.meta.env.VITE_APP_DEFAULT_LOGIN_TENANT || '',
    username: import.meta.env.VITE_APP_DEFAULT_LOGIN_USERNAME || '',
    password: import.meta.env.VITE_APP_DEFAULT_LOGIN_PASSWORD || '',
    captchaVerification: '',
    rememberMe: false // CO-TI: computador compartilhado não guarda o usuário sem a pessoa pedir
  }
})

// 获取验证码
const getCode = async () => {
  // 情况一，未开启：则直接登录
  if (loginData.captchaEnable === 'false') {
    await handleLogin({})
  } else {
    // 情况二，已开启：则展示验证码；只有完成验证码的情况，才进行登录
    // 弹出验证码
    verify.value.show()
  }
}
// 获取租户 ID
const getTenantId = async () => {
  if (loginData.tenantEnable === 'true') {
    const res = await LoginApi.getTenantIdByName(loginData.loginForm.tenantName)
    authUtil.setTenantId(res)
  }
}
// 记住我
const getLoginFormCache = () => {
  const loginForm = authUtil.getLoginForm()
  if (loginForm) {
    loginData.loginForm = {
      ...loginData.loginForm,
      username: loginForm.username ? loginForm.username : loginData.loginForm.username,
      password: loginForm.password ? loginForm.password : loginData.loginForm.password,
      rememberMe: loginForm.rememberMe,
      tenantName: loginForm.tenantName ? loginForm.tenantName : loginData.loginForm.tenantName
    }
  }
}
// 根据域名，获得租户信息
const getTenantByWebsite = async () => {
  if (loginData.tenantEnable === 'true') {
    const website = location.host
    const res = await LoginApi.getTenantByWebsite(website)
    if (res) {
      loginData.loginForm.tenantName = res.name
      authUtil.setTenantId(res.id)
    }
  }
}
const loading = ref() // ElLoading.service 返回的实例
// 登录
const handleLogin = async (params: any) => {
  loginLoading.value = true
  try {
    await getTenantId()
    const data = await validForm()
    if (!data) {
      return
    }
    const loginDataLoginForm = { ...loginData.loginForm }
    loginDataLoginForm.captchaVerification = params.captchaVerification
    const res = await LoginApi.login(loginDataLoginForm)
    if (!res) {
      return
    }
    if (res.mfaPendente) {
      mfa.desafio = res.desafioMfa
      mfa.codigo = ''
      mfa.formulario = loginDataLoginForm
      await nextTick()
      campoCodigoMfa.value?.focus()
      return
    }
    await entrar(res, loginDataLoginForm)
  } finally {
    loginLoading.value = false
    loading.value?.close()
  }
}

// CO-TI: segunda etapa
const enviarCodigoMfa = async () => {
  const valido = await formMfa.value?.validate().catch(() => false)
  if (!valido) {
    return
  }
  loginLoading.value = true
  try {
    const res = await LoginApi.loginMfa({ desafio: mfa.desafio, codigo: mfa.codigo.trim() })
    if (!res) {
      return
    }
    const formulario = mfa.formulario
    mfa.desafio = ''
    await entrar(res, formulario)
  } catch (e: any) {
    // desafio expirado ou tentativas esgotadas: volta para usuário e senha
    if (e?.code === 1002000050) {
      cancelarMfa()
    }
    mfa.codigo = ''
  } finally {
    loginLoading.value = false
    loading.value?.close()
  }
}

const cancelarMfa = () => {
  mfa.desafio = ''
  mfa.codigo = ''
  mfa.formulario = null
}

const entrar = async (res: any, loginDataLoginForm: any) => {
  {
    loading.value = ElLoading.service({
      lock: true,
      text: t('coti.carregandoSistema'),
      background: 'rgba(0, 0, 0, 0.7)'
    })
    if (loginDataLoginForm.rememberMe) {
      authUtil.setLoginForm(loginDataLoginForm)
    } else {
      authUtil.removeLoginForm()
    }
    authUtil.setToken(res)
    if (!redirect.value) {
      redirect.value = '/'
    }
    // 判断是否为SSO登录
    if (redirect.value.indexOf('sso') !== -1) {
      window.location.href = window.location.href.replace('/login?redirect=', '')
    } else {
      await push({ path: redirect.value || permissionStore.addRouters[0].path })
    }
  }
}

watch(
  () => currentRoute.value,
  (route: RouteLocationNormalizedLoaded) => {
    redirect.value = route?.query?.redirect as string
  },
  {
    immediate: true
  }
)
onMounted(() => {
  getLoginFormCache()
  getTenantByWebsite()
  assistenteDisponivel()
    .then((v) => (assistente.value = !!v))
    .catch(() => (assistente.value = false))
})
</script>

<style lang="scss" scoped>
:deep(.anticon) {
  &:hover {
    color: var(--el-color-primary) !important;
  }
}

.dica-login {
  display: flex;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.3;
  color: var(--el-text-color-secondary);
  align-items: center;
  gap: 4px;

  &--alerta {
    font-weight: 500;
    color: var(--el-color-warning);
  }
}

.login-code {
  float: right;
  width: 100%;
  height: 38px;

  img {
    width: 100%;
    height: auto;
    max-width: 100px;
    vertical-align: middle;
    cursor: pointer;
  }
}
</style>
