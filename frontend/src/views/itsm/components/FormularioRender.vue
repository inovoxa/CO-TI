<template>
  <el-form
    ref="formRef"
    :model="valores"
    :rules="regras"
    label-position="top"
    :disabled="desabilitado"
  >
    <template v-for="(s, i) in definicao.secoes" :key="i">
      <div v-if="definicao.secoes.length > 1 || s.nome" class="secao">{{ s.nome }}</div>
      <el-row :gutter="16">
        <el-col v-for="p in s.perguntas" :key="p.name" :md="larga(p) ? 24 : 12" :span="24">
          <el-form-item :label="p.label" :prop="p.name">
            <el-input
              v-if="p.type === 'textarea'"
              v-model="valores[p.name]"
              :autosize="{ minRows: 3, maxRows: 8 }"
              maxlength="10000"
              type="textarea"
            />
            <el-select
              v-else-if="p.type === 'select'"
              v-model="valores[p.name]"
              class="w-full"
              clearable
              filterable
            >
              <el-option v-for="v in p.values" :key="v" :label="v" :value="v" />
            </el-select>
            <el-radio-group v-else-if="p.type === 'radios'" v-model="valores[p.name]">
              <el-radio v-for="v in p.values" :key="v" :value="v">{{ v }}</el-radio>
            </el-radio-group>
            <LoginRedeSelect v-else-if="p.type === 'usuario'" v-model="valores[p.name]" />
            <el-date-picker
              v-else-if="p.type === 'date'"
              v-model="valores[p.name]"
              class="!w-full"
              format="DD/MM/YYYY"
              placeholder="dd/mm/aaaa"
              type="date"
              value-format="DD/MM/YYYY"
            />
            <el-input
              v-else
              v-model="valores[p.name]"
              :inputmode="
                p.type === 'number' ? 'decimal' : p.type === 'email' ? 'email' : undefined
              "
              :placeholder="p.type === 'location' ? 'Local ou unidade' : undefined"
              maxlength="500"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </el-form>
</template>

<script lang="ts" setup>
import type { FormItemRule } from 'element-plus'
import type { Definicao, Pergunta } from '@/api/itsm'
import LoginRedeSelect from './LoginRedeSelect.vue'

/** Desenha e valida um formulário do catálogo (a validação final é do servidor). */
const props = defineProps<{ definicao: Definicao; desabilitado?: boolean }>()
const valores = defineModel<Record<string, string>>({ required: true })
const formRef = ref()

const EMAIL = /^[^@\s]{1,64}@[^@\s]{1,190}\.[^@\s]{2,}$/
const NUMERO = /^-?\d{1,15}([.,]\d{1,6})?$/

/** "/^[0-9]{11}$/i" (estilo JavaScript, como no formularios_definicoes.json) ou a expressão pura. */
const expressao = (r?: string): RegExp | null => {
  if (!r) return null
  try {
    const m = r.trim().match(/^\/(.*)\/([a-z]*)$/s)
    return m ? new RegExp(m[1], m[2].includes('i') ? 'iu' : 'u') : new RegExp(r, 'u')
  } catch {
    return null
  }
}

const larga = (p: Pergunta) => p.type === 'textarea' || p.type === 'radios' || p.label.length > 60

const regras = computed(() => {
  const r: Record<string, FormItemRule[]> = {}
  for (const s of props.definicao.secoes) {
    for (const p of s.perguntas) {
      const lista: FormItemRule[] = []
      if (p.required) {
        lista.push({
          required: true,
          message: 'Obrigatório',
          trigger:
            p.type === 'select' || p.type === 'radios' || p.type === 'date' || p.type === 'usuario'
              ? 'change'
              : 'blur'
        })
      }
      const formato =
        p.type === 'email'
          ? EMAIL
          : p.type === 'number'
            ? NUMERO
            : p.type === 'regex'
              ? expressao(p.regex)
              : null
      if (formato) {
        lista.push({
          trigger: 'blur',
          validator: (_r, v, cb) =>
            !v || formato.test(String(v).trim())
              ? cb()
              : cb(
                  new Error(
                    p.type === 'email'
                      ? 'E-mail inválido'
                      : p.type === 'number'
                        ? 'Número inválido'
                        : 'Formato inválido'
                  )
                )
        })
      }
      r[p.name] = lista
    }
  }
  return r
})

const validar = async (): Promise<boolean> =>
  (await formRef.value?.validate().catch(() => false)) === true
const limpar = () => formRef.value?.clearValidate()

defineExpose({ validar, limpar })
</script>

<style scoped>
.secao {
  margin: 4px 0 12px;
  padding-bottom: 6px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
