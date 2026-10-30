<template>
  <div
    :class="prefixCls"
    class="relative h-[100%] lt-md:px-10px lt-sm:px-10px lt-xl:px-10px lt-xl:px-10px"
  >
    <div class="relative mx-auto h-full flex">
      <div :class="`${prefixCls}__left relative flex-1 overflow-hidden lt-xl:hidden`">
        <!-- foto da instância (Sistema > Identidade visual) -->
        <img :src="marca.fundoUrl" alt="" :class="`${prefixCls}__foto`" />
        <div :class="`${prefixCls}__veu`"></div>
        <div class="relative h-full flex flex-col justify-between p-40px text-white">
          <div :class="`${prefixCls}__selo`">
            <img alt="" class="h-40px w-40px object-contain" :src="marca.logoUrl" />
            <span class="ml-12px text-18px font-bold">{{
              underlineToHump(appStore.getTitle)
            }}</span>
          </div>
          <Transition appear enter-active-class="animate__animated animate__fadeInUp">
            <div :class="`${prefixCls}__texto`">
              <h1 :class="`${prefixCls}__titulo`">{{ t('login.welcome') }}</h1>
              <!-- 1ª linha em destaque; as demais viram itens com marcador -->
              <p :class="`${prefixCls}__lead`">{{ frases[0] }}</p>
              <ul v-if="frases.length > 1" :class="`${prefixCls}__itens`">
                <li v-for="(f, i) in frases.slice(1)" :key="i">
                  <Icon
                    color="#5fd3a6"
                    icon="ep:circle-check-filled"
                    :size="18"
                    class="mt-1px shrink-0"
                  />
                  <span>{{ f }}</span>
                </li>
              </ul>
            </div>
          </Transition>
        </div>
      </div>
      <div
        class="relative flex-1 p-30px dark:bg-[var(--login-bg-color)] lt-sm:p-10px overflow-x-hidden overflow-y-auto"
      >
        <!-- 右上角的主题 -->
        <div
          class="flex items-center justify-between at-2xl:justify-end at-xl:justify-end"
          style="color: var(--el-text-color-primary)"
        >
          <div class="flex items-center at-2xl:hidden at-xl:hidden">
            <img alt="" class="mr-10px h-48px w-48px object-contain" :src="marca.logoUrl" />
            <span class="text-20px font-bold">{{ underlineToHump(appStore.getTitle) }}</span>
          </div>
          <div class="flex items-center justify-end space-x-10px h-48px">
            <ThemeSwitch />
          </div>
        </div>
        <!-- 右边的登录界面 -->
        <Transition appear enter-active-class="animate__animated animate__fadeIn">
          <div
            class="m-auto h-[calc(100%-60px)] w-[100%] flex items-center at-2xl:max-w-500px at-lg:max-w-500px at-md:max-w-500px at-xl:max-w-500px"
          >
            <!-- 账号登录 -->
            <LoginForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 手机登录 -->
            <MobileForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 二维码登录 -->
            <QrCodeForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 注册 -->
            <RegisterForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 三方登录 -->
            <SSOLoginVue class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 忘记密码 -->
            <ForgetPasswordForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
          </div>
        </Transition>
        <div class="absolute bottom-14px left-0 right-0 text-center">
          <InovoxaAssinatura />
        </div>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { underlineToHump } from '@/utils'

import { useDesign } from '@/hooks/web/useDesign'
import { useAppStore } from '@/store/modules/app'
import { useMarcaStore } from '@/store/modules/marca'
import InovoxaAssinatura from '@/components/Inovoxa/index.vue'
import { ThemeSwitch } from '@/layout/components/ThemeSwitch'

import {
  LoginForm,
  MobileForm,
  QrCodeForm,
  RegisterForm,
  SSOLoginVue,
  ForgetPasswordForm
} from './components'

defineOptions({ name: 'Login' })

const { t } = useI18n()
const appStore = useAppStore()
const marca = useMarcaStore()
const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('login')
const frases = computed(() =>
  marca.fraseLogin
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
)
</script>

<style lang="scss" scoped>
$prefix-cls: #{$namespace}-login;

.#{$prefix-cls} {
  overflow: auto;

  &__foto {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    animation: login-foto 30s ease-in-out infinite alternate;
  }

  /* escurece embaixo para o texto ler bem sobre qualquer foto */
  &__veu {
    position: absolute;
    inset: 0;
    background:
      /* rodapé: sobe de baixo até passar do título, destacando as frases */
      linear-gradient(
        0deg,
        rgb(6 14 26 / 96%) 0%,
        rgb(6 14 26 / 88%) 22%,
        rgb(6 14 26 / 62%) 40%,
        rgb(6 14 26 / 20%) 55%,
        rgb(6 14 26 / 0%) 68%
      ),
      /* topo: leve sombra atrás do selo da logo */
      linear-gradient(180deg, rgb(6 14 26 / 40%) 0%, rgb(6 14 26 / 0%) 22%),
      linear-gradient(
        120deg,
        rgb(43 161 119 / 18%),
        rgb(58 111 176 / 12%) 50%,
        rgb(184 96 159 / 18%)
      );
  }

  &__selo {
    display: inline-flex;
    padding: 8px 16px 8px 8px;
    text-shadow: 0 1px 2px rgb(0 0 0 / 30%);
    background: rgb(255 255 255 / 14%);
    border: 1px solid rgb(255 255 255 / 22%);
    border-radius: 14px;
    backdrop-filter: blur(10px);
    align-items: center;
    align-self: flex-start;
  }

  &__texto {
    max-width: 540px;
    text-shadow: 0 1px 14px rgb(0 0 0 / 35%);
  }

  &__titulo {
    margin: 0;
    font-size: 46px;
    font-weight: 700;
    line-height: 1.1;
    letter-spacing: -0.02em;
  }

  &__lead {
    margin: 14px 0 0;
    font-size: 18px;
    font-weight: 500;
    line-height: 1.5;
    color: rgb(255 255 255 / 95%);
  }

  &__itens {
    display: flex;
    padding: 14px 18px;
    margin: 20px 0 0;
    list-style: none;
    background: rgb(255 255 255 / 8%);
    border: 1px solid rgb(255 255 255 / 14%);
    border-radius: 12px;
    backdrop-filter: blur(8px);
    flex-direction: column;
    gap: 8px;

    li {
      display: flex;
      font-size: 15px;
      line-height: 1.45;
      color: rgb(255 255 255 / 88%);
      gap: 10px;
      align-items: flex-start;
    }
  }
}

@keyframes login-foto {
  from {
    transform: scale(1);
  }

  to {
    transform: scale(1.08);
  }
}

@media (prefers-reduced-motion: reduce) {
  .#{$prefix-cls}__foto {
    animation: none;
  }
}
</style>

<style lang="scss">
.dark .login-form {
  .el-divider__text {
    background-color: var(--login-bg-color);
  }

  .el-card {
    background-color: var(--login-bg-color);
  }
}
</style>
