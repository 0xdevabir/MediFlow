from eval.metrics_lib import cer, expected_calibration_error, field_f1, topk_hit


def test_cer_identical_and_edit():
    assert cer("abc", "abc") == 0.0
    assert cer("ab", "abc") == 1 / 3


def test_topk_and_f1_and_ece():
    assert topk_hit("Paracetamol", "Paracetamol", [], k=1)
    assert not topk_hit("Ibuprofen", "Paracetamol", ["Aspirin"], k=1)
    assert topk_hit("Ibuprofen", "Paracetamol", ["Paracetamol", "Aspirin"], k=3) is False
    assert topk_hit("Paracetamol", "Paracetamol", ["X"], k=3)

    f = field_f1(["Paracetamol", None], ["Paracetamol", "Ibuprofen"])
    assert f["tp"] == 1 and f["fn"] == 1

    ece, diagram = expected_calibration_error([0.9, 0.2], [True, False], n_bins=2)
    assert 0.0 <= ece <= 1.0
    assert len(diagram) == 2

