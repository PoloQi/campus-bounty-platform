import { h } from 'vue'
import { ElMessageBox } from 'element-plus'
import ConfirmBody from '@/components/ConfirmBody.vue'

export interface ConfirmRow {
  label: string
  value: string
}

export interface ConfirmSpec {
  title: string
  rows?: ConfirmRow[]
  impact?: string
  confirmText?: string
  cancelText?: string
}

/**
 * 二次确认。先确认、后请求 —— 不要把请求塞进确认框：
 * ElMessageBox 不支持按钮 loading 与内联错误区，塞进去会出现「点了没反应」。
 *
 * 内容用 VNode 注入而不是 dangerouslyUseHTMLString：
 * 帖子标题、用户昵称都是用户输入，拼 HTML 会有 XSS 风险。
 */
export function useConfirm() {
  async function confirm(spec: ConfirmSpec): Promise<boolean> {
    try {
      await ElMessageBox({
        title: spec.title,
        message: h(ConfirmBody, { rows: spec.rows, impact: spec.impact }),
        confirmButtonText: spec.confirmText ?? '确认',
        cancelButtonText: spec.cancelText ?? '返回',
        type: 'warning',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
        customClass: 'admin-confirm-box',
      })
      return true
    } catch {
      return false
    }
  }

  return { confirm }
}
