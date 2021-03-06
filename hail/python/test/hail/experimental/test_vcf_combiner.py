import os

import hail as hl
from hail.experimental import vcf_combiner as vc
from hail.utils.java import Env
from hail.utils.misc import new_temp_file
from ..helpers import resource

all_samples = ['HG00308', 'HG00592', 'HG02230', 'NA18534', 'NA20760',
               'NA18530', 'HG03805', 'HG02223', 'HG00637', 'NA12249',
               'HG02224', 'NA21099', 'NA11830', 'HG01378', 'HG00187',
               'HG01356', 'HG02188', 'NA20769', 'HG00190', 'NA18618',
               'NA18507', 'HG03363', 'NA21123', 'HG03088', 'NA21122',
               'HG00373', 'HG01058', 'HG00524', 'NA18969', 'HG03833',
               'HG04158', 'HG03578', 'HG00339', 'HG00313', 'NA20317',
               'HG00553', 'HG01357', 'NA19747', 'NA18609', 'HG01377',
               'NA19456', 'HG00590', 'HG01383', 'HG00320', 'HG04001',
               'NA20796', 'HG00323', 'HG01384', 'NA18613', 'NA20802']


def test_1kg_chr22():
    out_file = new_temp_file(suffix='mt')

    sample_names = all_samples[:5]
    paths = [os.path.join(resource('gvcfs'), '1kg_chr22', f'{s}.hg38.g.vcf.gz') for s in sample_names]
    vc.run_combiner(paths,
                    out_file=out_file,
                    tmp_path=Env.hc().tmp_dir,
                    branch_factor=2,
                    batch_size=2,
                    reference_genome='GRCh38')

    sample_data = dict()
    for sample, path in zip(sample_names, paths):
        ht = hl.import_vcf(path, force_bgz=True, reference_genome='GRCh38').localize_entries('entries')
        n, n_variant = ht.aggregate((hl.agg.count(), hl.agg.count_where(ht.entries[0].GT.is_non_ref())))
        sample_data[sample] = (n, n_variant)

    mt = hl.read_matrix_table(out_file)
    mt = mt.annotate_cols(n=hl.agg.count(), n_variant=hl.agg.count_where(
        mt.LGT.is_non_ref()))  # annotate the number of non-missing records

    combined_results = hl.tuple([mt.s, mt.n, mt.n_variant]).collect()
    assert len(combined_results) == len(sample_names)

    for sample, n, n_variant in combined_results:
        true_n, true_n_variant = sample_data[sample]
        assert n == true_n, sample
        assert n_variant == true_n_variant, sample
