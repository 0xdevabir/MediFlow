package com.mediflow.lab.web;

import com.mediflow.lab.service.LabQueryService;
import com.mediflow.lab.service.LabViews;
import com.mediflow.lab.service.LabWorkflowService;
import com.mediflow.shared.domain.LabOrderStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/lab/queue")
@PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
public class LabTechController {

  private final LabQueryService queries;
  private final LabWorkflowService workflow;

  public LabTechController(LabQueryService queries, LabWorkflowService workflow) {
    this.queries = queries;
    this.workflow = workflow;
  }

  @GetMapping
  public String queue(
      @RequestParam(required = false) LabOrderStatus status, Model model) {
    model.addAttribute("orders", queries.techQueue(status));
    model.addAttribute("status", status);
    model.addAttribute("statuses", LabOrderStatus.values());
    model.addAttribute("criticalTasks", queries.openCriticalTasks());
    return "lab/queue";
  }

  @GetMapping("/{orderId}")
  public String order(@PathVariable UUID orderId, Model model) {
    LabViews.TechOrderView view = queries.techOrder(orderId);
    model.addAttribute("view", view);
    model.addAttribute("qrToken", queries.qrToken(orderId));
    return "lab/tech-order";
  }

  @PostMapping("/{orderId}/collect")
  public String collect(
      @PathVariable UUID orderId,
      @RequestParam String qrToken,
      RedirectAttributes flash) {
    workflow.collectSample(orderId, qrToken);
    flash.addFlashAttribute("successKey", "lab.tech.collected");
    return "redirect:/lab/queue/" + orderId;
  }

  @PostMapping("/{orderId}/receive")
  public String receive(@PathVariable UUID orderId, RedirectAttributes flash) {
    workflow.receiveSample(orderId);
    flash.addFlashAttribute("successKey", "lab.tech.received");
    return "redirect:/lab/queue/" + orderId;
  }

  @PostMapping("/{orderId}/results")
  public String upload(
      @PathVariable UUID orderId,
      @RequestParam(required = false) MultipartFile pdf,
      @RequestParam(required = false) String summary,
      @RequestParam List<String> analyteCode,
      @RequestParam(required = false) List<String> valueNumeric,
      @RequestParam(required = false) List<String> valueText,
      @RequestParam(required = false) List<String> abnormalText,
      RedirectAttributes flash)
      throws Exception {
    List<LabWorkflowService.AnalyteInput> inputs = new ArrayList<>();
    if (analyteCode == null) {
      analyteCode = List.of();
    }
    for (int i = 0; i < analyteCode.size(); i++) {
      String code = analyteCode.get(i);
      BigDecimal num = null;
      if (valueNumeric != null && i < valueNumeric.size() && valueNumeric.get(i) != null) {
        String raw = valueNumeric.get(i).trim();
        if (!raw.isEmpty()) {
          num = new BigDecimal(raw);
        }
      }
      String text =
          valueText != null && i < valueText.size() ? valueText.get(i) : null;
      boolean abn =
          abnormalText != null
              && i < abnormalText.size()
              && "true".equalsIgnoreCase(abnormalText.get(i));
      inputs.add(new LabWorkflowService.AnalyteInput(code, null, null, null, num, text, abn));
    }
    byte[] pdfBytes = null;
    if (pdf != null && !pdf.isEmpty()) {
      pdfBytes = pdf.getBytes();
    }
    workflow.uploadResults(orderId, pdfBytes, summary, inputs);
    flash.addFlashAttribute("successKey", "lab.tech.uploaded");
    return "redirect:/lab/queue/" + orderId;
  }

  @PostMapping("/{orderId}/verify")
  public String verify(@PathVariable UUID orderId, RedirectAttributes flash) {
    workflow.verify(orderId);
    flash.addFlashAttribute("successKey", "lab.tech.verified");
    return "redirect:/lab/queue/" + orderId;
  }

  @PostMapping("/critical/{taskId}/done")
  public String completeCritical(
      @PathVariable UUID taskId,
      @RequestParam String outcomeNote,
      RedirectAttributes flash) {
    workflow.completeCriticalTask(taskId, outcomeNote);
    flash.addFlashAttribute("successKey", "lab.tech.criticalDone");
    return "redirect:/lab/queue";
  }
}

